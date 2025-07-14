// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.newImpl.ExtractException;
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodAnalyzerKt;
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ExtractMethodRecommenderInspection extends AbstractBaseJavaLocalInspectionTool {
  public int minLength = 500;
  public int maxParameters = 3;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("minLength", JavaAnalysisBundle.message("inspection.extract.method.option.min.length"), 10, 10_000),
      number("maxParameters", JavaAnalysisBundle.message("inspection.extract.method.option.max.parameters"), 1, 10)
    );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitCodeBlock(@NotNull PsiCodeBlock block) {
        if (block.getTextLength() < minLength) return;
        PsiParameterListOwner container = PsiTreeUtil.getParentOfType(block, PsiParameterListOwner.class);
        if (container == null) return;
        PsiElement body = container.getBody();
        if (body == null) return;
        PsiStatement[] statements = block.getStatements();
        BitSet declarations = getDeclarations(statements);
        if (declarations.isEmpty()) return;
        int maxLength = body.getTextLength() * 3 / 5;
        if (maxLength < minLength) return;
        int maxCount;
        if (block == body) {
          maxCount = statements.length - 1;
          if (ArrayUtil.getLastElement(statements) instanceof PsiReturnStatement) {
            maxCount--;
          }
        }
        else {
          maxCount = statements.length;
        }
        PsiElement fragment = ControlFlowUtil.findCodeFragment(block);
        for (int count = maxCount; count > 1; count--) {
          for (int from = 0; from < statements.length - count; from++) {
            int to = from + count;
            
            // Check that we have at least one declaration
            int nextDeclaration = declarations.nextSetBit(from);
            if (nextDeclaration == -1 || nextDeclaration >= to) continue;
            PsiStatement[] range = Arrays.copyOfRange(statements, from, to);
            if (ContainerUtil.exists(range, e -> e instanceof PsiSwitchLabelStatementBase)) continue;
            TextRange textRange = getRange(range);
            int length = range[range.length - 1].getTextRange().getEndOffset() - range[0].getTextRange().getStartOffset();
            if (length < minLength || textRange.getLength() > maxLength) continue;
            try {
              ControlFlowWrapper wrapper = new ControlFlowWrapper(fragment, range);
              Collection<PsiStatement> exitStatements = wrapper.prepareExitStatements(range);
              if (!exitStatements.isEmpty()) continue;
              if (wrapper.isGenerateConditionalExit() || wrapper.isReturnPresentBetween()) continue;
              PsiVariable[] variables = wrapper.getOutputVariables(2);
              if (variables.length != 1) continue;
              PsiVariable output = variables[0];
              if (SideEffectsVisitor.hasSideEffectOrSimilarUseOutside(range, output)) continue;
              PsiTypeElement typeElement = output.getTypeElement();
              if (typeElement == null || (typeElement.isInferredType() && !PsiTypesUtil.isDenotableType(output.getType(), output))) {
                continue;
              }

              List<PsiVariable> inputVariables = wrapper.getInputVariables(fragment, range, variables);
              if (inputVariables.size() > maxParameters) continue;
              ExtractMethodAnalyzerKt.findExtractOptions(Arrays.asList(range), false); // check whether ExtractException will happen
              if (voidPrefix(fragment, range, output)) continue;
              if (!outputUsedInLastStatement(range, output)) continue;
              wrapper.checkExitStatements(range, fragment);
              if (to < statements.length) {
                PsiStatement nextStatement = statements[to];
                if (nextStatement instanceof PsiReturnStatement ret && 
                    ExpressionUtils.isReferenceTo(ret.getReturnValue(), output)) {
                  textRange = textRange.union(ret.getTextRangeInParent());
                }
              }
              List<LocalQuickFix> fixes = new ArrayList<>();
              ExtractMethodFix extractFix = new ExtractMethodFix(from, count, output, inputVariables);
              fixes.add(extractFix);
              if (inputVariables.size() > 1) {
                fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
                  ExtractMethodRecommenderInspection.this, "maxParameters",
                  JavaAnalysisBundle.message("inspection.extract.method.dont.suggest.parameters", inputVariables.size()),
                  inputVariables.size() - 1)));
              }
              if (length < 10_000) {
                fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
                  ExtractMethodRecommenderInspection.this, "minLength",
                  JavaAnalysisBundle.message("inspection.extract.method.dont.suggest.length"),
                  length + 1)));
              }
              int firstLineBreak = textRange.substring(block.getText()).indexOf('\n');
              PsiElement anchor = block;
              if (firstLineBreak > -1) {
                textRange = TextRange.from(textRange.getStartOffset(), firstLineBreak);
                TextRange firstStatementRange = statements[from].getTextRangeInParent();
                if (firstStatementRange.getStartOffset() == textRange.getStartOffset() &&
                    firstStatementRange.getEndOffset() >= textRange.getEndOffset()) {
                  anchor = statements[from];
                  extractFix.shouldUseParent();
                  textRange = textRange.shiftLeft(textRange.getStartOffset());
                }
              }
              holder.registerProblem(anchor, JavaAnalysisBundle.message("inspection.extract.method.message", output.getName()),
                                     ProblemHighlightType.WEAK_WARNING,
                                     textRange,
                                     fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
              return;
            }
            catch (PrepareFailedException | ControlFlowWrapper.ExitStatementsNotSameException | ExtractException ignored) {
            }
          }
        }
      }

      private static boolean outputUsedInLastStatement(PsiStatement[] range, PsiVariable output) {
        int outDecl = Arrays.asList(range).indexOf(output.getParent());
        return outDecl >= range.length - 1 || VariableAccessUtils.variableIsUsed(output, range[range.length - 1]);
      }

      private static @NotNull BitSet getDeclarations(PsiStatement[] statements) {
        BitSet declarations = new BitSet();
        for (int i = 0; i < statements.length; i++) {
          if (statements[i] instanceof PsiDeclarationStatement decl &&
              ArrayUtil.getFirstElement(decl.getDeclaredElements()) instanceof PsiLocalVariable) {
            declarations.set(i);
          }
        }
        return declarations;
      }

      private static boolean voidPrefix(@NotNull PsiElement fragment, @NotNull PsiStatement @NotNull [] range, @NotNull PsiVariable output)
        throws PrepareFailedException {
        if (output.getParent() instanceof PsiDeclarationStatement statement) {
          int declarationIndex = Arrays.asList(range).indexOf(statement);
          if (declarationIndex > 0) {
            PsiStatement[] subRange = Arrays.copyOf(range, declarationIndex);
            ControlFlowWrapper subWrapper = new ControlFlowWrapper(fragment, subRange);
            subWrapper.prepareExitStatements(subRange);
            return subWrapper.getOutputVariables(1).length == 0;
          }
        }
        return false;
      }

      private static @NotNull TextRange getRange(PsiStatement[] statements) {
        PsiElement start = statements[0];
        //while (true) {
        //  PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(start);
        //  if (prev instanceof PsiComment &&
        //      SuppressionUtil.getStatementToolSuppressedIn(statements[0], "ExtractMethodRecommender", PsiStatement.class) == null) {
        //    start = prev;
        //  }
        //  else {
        //    break;
        //  }
        //}
        int startOffset = start.getTextRangeInParent().getStartOffset();
        int endOffset = statements[statements.length - 1].getTextRangeInParent().getEndOffset();
        return TextRange.create(startOffset, endOffset);
      }
    };
  }

  private static class SideEffectsVisitor extends JavaRecursiveElementWalkingVisitor {
    private final @NotNull PsiElement myStartElement;
    private final @NotNull PsiElement myEndElement;
    private final @NotNull PsiVariable myVariable;
    private final @NotNull PsiElement myParent;
    boolean found;

    SideEffectsVisitor(@NotNull PsiStatement @NotNull [] statements, @NotNull PsiVariable variable) {
      myStartElement = statements[0];
      myEndElement = statements[statements.length - 1];
      myVariable = variable;
      PsiElement parent = myStartElement.getParent();
      if (myEndElement.getParent() != parent) {
        throw new IllegalArgumentException();
      }
      myParent = parent;
    }

    private void addSideEffect() {
      found = true;
      stopWalking();
    }

    private boolean isInside(@Nullable PsiElement element) {
      if (element == null) return false;
      PsiElement cur = element;
      PsiElement next = cur.getParent();
      while (next != null) {
        if (cur == myStartElement ||
            cur == myEndElement ||
            next == myParent && cur.getTextRangeInParent().getStartOffset() >= myStartElement.getTextRangeInParent().getStartOffset() &&
            cur.getTextRangeInParent().getEndOffset() <= myEndElement.getTextRangeInParent().getEndOffset()) {
          return true;
        }
        cur = next;
        next = cur.getParent();
      }
      return false;
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(expression.getLExpression());
      if (!isInside(variable)) {
        addSideEffect();
      }
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      MutationSignature signature = MutationSignature.fromCall(expression);
      if (!assumeOkMethod(expression) &&
          (ExpressionUtils.isVoidContext(expression) ||
           signature == MutationSignature.unknown() || signature.performsIO() ||
           signature.mutatedExpressions(expression).map(ExpressionUtils::resolveLocalVariable)
             .anyMatch(var -> !isInside(var)))) {
        addSideEffect();
        return;
      }
      super.visitMethodCallExpression(expression);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      if (ExpressionUtils.isVoidContext(expression)) {
        addSideEffect();
        return;
      }
      super.visitNewExpression(expression);
    }

    private boolean assumeOkMethod(@NotNull PsiMethodCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      if (method.getName().startsWith("assert") && ExpressionUtils.isVoidContext(call)) return false;
      if (PropertyUtilBase.isSimplePropertyGetter(method)) return true;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (PropertyUtilBase.isSimplePropertySetter(method) && ExpressionUtils.resolveLocalVariable(qualifier) == myVariable) {
        return true;
      }
      for (PsiExpression expr : ArrayUtil.append(args, qualifier)) {
        if (expr != null) {
          PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(expr);
          if (expr instanceof PsiLambdaExpression lambda &&
              lambda.getBody() != null &&
              SideEffectChecker.mayHaveNonLocalSideEffects(lambda.getBody())) {
            continue;
          }
          if (!isInside(variable) && !ClassUtils.isImmutable(expr.getType())) return false;
        }
      }
      return true;
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      if (variable != myVariable) {
        for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(variable, myParent)) {
          if (!isInside(reference)) {
            addSideEffect();
            return;
          }
        }
      }
      super.visitLocalVariable(variable);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS)) {
        PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(expression.getOperand());
        if (!isInside(variable)) {
          addSideEffect();
        }
        return;
      }
      super.visitUnaryExpression(expression);
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      PsiStatement exitedStatement = statement.findExitedStatement();
      if (!isInside(exitedStatement)) {
        addSideEffect();
        return;
      }
      super.visitBreakStatement(statement);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (ReferencesSearch.search(aClass, new LocalSearchScope(myParent))
        .anyMatch(ref -> !isInside(ref.getElement()))) {
        addSideEffect();
      }
      // local or anonymous class declaration is not side effect per se (unless referenced from outside of block)
    }

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      PsiStatement exitedStatement = statement.findContinuedStatement();
      if (!isInside(exitedStatement)) {
        addSideEffect();
        return;
      }
      super.visitContinueStatement(statement);
    }

    @Override
    public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
      final PsiSwitchExpression enclosingExpression = statement.findEnclosingExpression();
      if (!isInside(enclosingExpression)) {
        addSideEffect();
        return;
      }
      super.visitYieldStatement(statement);
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      addSideEffect();
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      // lambda is not side effect per se (unless it's called)
    }

    static boolean hasSideEffectOrSimilarUseOutside(@NotNull PsiStatement @NotNull [] statements, PsiVariable variable) {
      SideEffectsVisitor visitor = new SideEffectsVisitor(statements, variable);
      for (PsiStatement statement : statements) {
        statement.accept(visitor);
        if (visitor.found) return true;
      }
      if (visitor.hasSimilarUseOfOutputInsideAndOutside(variable)) return true;
      return false;
    }

    private boolean hasSimilarUseOfOutputInsideAndOutside(PsiVariable variable) {
      Set<VarUseContext> insideContexts = new HashSet<>();
      Set<VarUseContext> outsideContexts = new HashSet<>();
      for (PsiReferenceExpression ref : VariableAccessUtils.getVariableReferences(variable, myParent)) {
        VarUseContext context = VarUseContext.from(ref);
        if (context != null) {
          (isInside(ref) ? insideContexts : outsideContexts).add(context);
        }
      }
      if (!Collections.disjoint(insideContexts, outsideContexts)) return true;
      return false;
    }
  }

  sealed interface VarUseContext {
    static @Nullable VarUseContext from(@NotNull PsiReferenceExpression ref) {
      if (PsiUtil.isAccessedForWriting(ref)) {
        return Contexts.WRITING;
      }
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(ref);
      if (call != null) {
        String name = call.getMethodExpression().getReferenceName();
        if (name != null) {
          if (PropertyUtilBase.isSimplePropertySetter(call.resolveMethod())) {
            return Contexts.SETTER;
          }
          if (name.matches("add[A-Z].+")) {
            return Contexts.ADDER;
          }
          return new QualifierContext(name);
        }
      }
      PsiParameter parameter = MethodCallUtils.getParameterForArgument(ref);
      if (parameter != null && parameter.getDeclarationScope() instanceof PsiMethod method) {
        return new ArgumentContext(method.getName(), method.getParameterList().getParameterIndex(parameter));
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if (parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement ||
          parent instanceof PsiPolyadicExpression poly &&
          TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.OROR).contains(poly.getOperationTokenType())) {
        return Contexts.CONDITION;
      }
      return null;
    }
  }

  enum Contexts implements VarUseContext {
    WRITING, CONDITION, SETTER, ADDER
  }

  record QualifierContext(@NotNull String name) implements VarUseContext {
  }

  record ArgumentContext(String methodName, int parameterIndex) implements VarUseContext {
  }
  
  private static class ExtractMethodFix implements LocalQuickFix {
    private final int myFrom;
    private final int myLength;
    private final String myOutputName;
    private final String myInputNames;

    private boolean shouldUseParent = false;

    private ExtractMethodFix(int from, int length, PsiVariable variable, List<PsiVariable> inputVariables) {
      myFrom = from;
      myLength = length;
      myOutputName = variable.getName();
      myInputNames = StreamEx.of(inputVariables).map(p -> p.getName()).joining(", ");
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("intention.extract.method.text");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (shouldUseParent) {
        element = element.getParent();
      }
      PsiCodeBlock block = ObjectUtils.tryCast(element, PsiCodeBlock.class);
      TextRange range = getRange(block);
      if (range == null) return;
      new MethodExtractor().doExtract(block.getContainingFile(), range.shiftRight(block.getTextRange().getStartOffset()));
    }

    @Override
    public @NotNull List<CustomizableIntentionAction.RangeToHighlight> getRangesToHighlight(Project project, ProblemDescriptor descriptor) {
      PsiCodeBlock block = ObjectUtils.tryCast(descriptor.getStartElement(), PsiCodeBlock.class);
      TextRange range = getRange(block);
      if (range == null) return List.of();
      return List.of(new CustomizableIntentionAction.RangeToHighlight(
        block, range, EditorColors.SEARCH_RESULT_ATTRIBUTES));
    }

    @Contract("null -> null")
    private @Nullable TextRange getRange(@Nullable PsiCodeBlock block) {
      if (block == null) return null;
      PsiStatement[] statements = block.getStatements();
      if (statements.length < myFrom + myLength) return null;
      return statements[myFrom].getTextRangeInParent().union(statements[myFrom + myLength - 1].getTextRangeInParent());
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      String input = myInputNames.isEmpty() ? JavaAnalysisBundle.message("inspection.extract.method.nothing") : "<b>(" + myInputNames + ")</b>";
      return new IntentionPreviewInfo.Html(
        JavaAnalysisBundle.message("inspection.extract.method.preview.html", myLength,input,myOutputName));
    }

    private void shouldUseParent() {
      shouldUseParent = true;
    }
  }
}
