/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.codeInspection.streamToLoop.Operation.FlatMapOperation;

/**
 * @author Tagir Valeev
 */
public class StreamToLoopInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(StreamToLoopInspection.class);

  // To quickly filter out most of the non-interesting method calls
  private static final Set<String> SUPPORTED_TERMINALS = ContainerUtil.set(
    "count", "sum", "summaryStatistics", "reduce", "collect", "findFirst", "findAny", "anyMatch", "allMatch", "noneMatch", "toArray",
    "average", "forEach", "forEachOrdered", "min", "max", "toList", "toSet");

  public boolean SUPPORT_UNKNOWN_SOURCES = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Iterate unknown Stream sources via Stream.iterator()", this, "SUPPORT_UNKNOWN_SOURCES");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        PsiReferenceExpression expression = call.getMethodExpression();
        PsiElement nameElement = expression.getReferenceNameElement();
        if (nameElement == null || !SUPPORTED_TERMINALS.contains(nameElement.getText()) || !ControlFlowUtils.canExtractStatement(call)) return;
        PsiMethod method = call.resolveMethod();
        if(method == null) return;
        PsiClass aClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
          if (extractOperations(StreamVariable.STUB, call, SUPPORT_UNKNOWN_SOURCES) != null) {
            register(call, nameElement, "Replace Stream API chain with loop");
          }
        }
        else if (extractIterableForEach(call) != null) {
          register(call, nameElement, "Replace 'forEach' call with loop");
        }
      }

      private void register(PsiMethodCallExpression call, PsiElement nameElement, String message) {
        TextRange range;
        if (isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), call)) {
          range = new TextRange(0, call.getTextLength());
        }
        else {
          range = nameElement.getTextRange().shiftRight(-call.getTextOffset());
        }
        holder.registerProblem(call, range, message, new ReplaceStreamWithLoopFix(message));
      }
    };
  }

  @Nullable
  static Operation createOperationFromCall(StreamVariable outVar, PsiMethodCallExpression call, boolean supportUnknownSources) {
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    PsiClass aClass = method.getContainingClass();
    if(aClass == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    String name = method.getName();
    String className = aClass.getQualifiedName();
    if(className == null) return null;
    PsiType callType = call.getType();
    if(callType == null) return null;
    if(InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM) &&
       !method.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if(qualifier != null) {
        PsiType elementType = StreamApiUtil.getStreamElementType(qualifier.getType());
        if (!isValidElementType(elementType, call, false)) return null;
        Operation op = Operation.createIntermediate(name, args, outVar, elementType, supportUnknownSources);
        if (op != null) return op;
        op = TerminalOperation.createTerminal(name, args, elementType, callType, isVoidContext(call.getParent()));
        if (op != null) return op;
      }
      return null;
    }
    return SourceOperation.createSource(call, supportUnknownSources);
  }

  private static boolean isValidElementType(PsiType elementType, PsiElement context, boolean allowRaw) {
    if(elementType == null || (!allowRaw && (elementType instanceof PsiClassType) && ((PsiClassType)elementType).isRaw())) {
      // Raw type in any stream step is not supported
      return false;
    }
    if(elementType instanceof PsiImmediateClassType) {
      PsiResolveHelper helper = PsiResolveHelper.SERVICE.getInstance(context.getProject());
      if (helper.resolveReferencedClass(elementType.getCanonicalText(), context) == null) {
        return false;
      }
    }
    return true;
  }

  private static boolean isVoidContext(PsiElement element) {
    return element instanceof PsiExpressionStatement ||
           (element instanceof PsiLambdaExpression &&
            PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)element)));
  }

  @Nullable
  static List<OperationRecord> extractIterableForEach(PsiMethodCallExpression terminalCall) {
    if (MethodCallUtils.isCallToMethod(terminalCall, CommonClassNames.JAVA_LANG_ITERABLE, PsiType.VOID, "forEach", new PsiType[1])
        && isVoidContext(terminalCall.getParent())) {
      PsiExpression qualifier = terminalCall.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return null;
      // Do not visit this path if some class implements both Iterable and Stream
      PsiType type = qualifier.getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) return null;
      PsiExpression[] args = terminalCall.getArgumentList().getExpressions();
      if (args.length != 1) return null;
      FunctionHelper fn = FunctionHelper.create(args[0], 1, true);
      if (fn == null) return null;
      PsiType elementType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
      if(!isValidElementType(elementType, terminalCall, true)) return null;
      elementType = GenericsUtil.getVariableTypeByExpressionType(elementType);
      TerminalOperation terminal = new TerminalOperation.ForEachTerminalOperation(fn);
      SourceOperation source = new SourceOperation.ForEachSource(qualifier);
      OperationRecord terminalRecord = new OperationRecord();
      OperationRecord sourceRecord = new OperationRecord();
      terminalRecord.myOperation = terminal;
      sourceRecord.myOperation = source;
      sourceRecord.myOutVar = terminalRecord.myInVar = new StreamVariable(elementType);
      sourceRecord.myInVar = terminalRecord.myOutVar = StreamVariable.STUB;
      return Arrays.asList(sourceRecord, terminalRecord);
    }
    return null;
  }

  @Nullable
  static List<OperationRecord> extractOperations(StreamVariable outVar,
                                                 PsiMethodCallExpression terminalCall,
                                                 boolean supportUnknownSources) {
    List<OperationRecord> operations = new ArrayList<>();
    PsiMethodCallExpression currentCall = terminalCall;
    StreamVariable lastVar = outVar;
    Operation next = null;
    while(true) {
      Operation op = createOperationFromCall(lastVar, currentCall, supportUnknownSources);
      if(op == null) return null;
      if(next != null) {
        Operation combined = op.combineWithNext(next);
        if (combined != null) {
          op = combined;
          operations.remove(operations.size() - 1);
        }
      }
      OperationRecord or = new OperationRecord();
      or.myOperation = op;
      or.myOutVar = lastVar;
      operations.add(or);
      if(op instanceof SourceOperation) {
        or.myInVar = StreamVariable.STUB;
        Collections.reverse(operations);
        return operations;
      }
      currentCall = MethodCallUtils.getQualifierMethodCall(currentCall);
      if(currentCall == null) return null;
      if(op.changesVariable()) {
        PsiType type = StreamApiUtil.getStreamElementType(currentCall.getType());
        if(type == null) return null;
        lastVar = new StreamVariable(type);
      }
      or.myInVar = lastVar;
      next = op;
    }
  }

  @Contract("null -> null")
  @Nullable
  static TerminalOperation getTerminal(List<OperationRecord> operations) {
    if (operations == null || operations.isEmpty()) return null;
    OperationRecord record = operations.get(operations.size()-1);
    if(record.myOperation instanceof TerminalOperation) {
      return (TerminalOperation)record.myOperation;
    }
    return null;
  }

  static class ReplaceStreamWithLoopFix implements LocalQuickFix {
    private String myMessage;

    public ReplaceStreamWithLoopFix(String message) {
      myMessage = message;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Stream API chain with loop";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression terminalCall = (PsiMethodCallExpression)element;
      if(!ControlFlowUtils.canExtractStatement(terminalCall)) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      terminalCall = RefactoringUtil.ensureCodeBlock(terminalCall);
      if (terminalCall == null) return;
      PsiType resultType = terminalCall.getType();
      if (resultType == null) return;
      List<OperationRecord> operations = extractOperations(StreamVariable.STUB, terminalCall, true);
      if (operations == null) {
        operations = extractIterableForEach(terminalCall);
      }
      TerminalOperation terminal = getTerminal(operations);
      if (terminal == null) return;
      PsiStatement statement = PsiTreeUtil.getParentOfType(terminalCall, PsiStatement.class);
      LOG.assertTrue(statement != null);
      CommentTracker ct = new CommentTracker();
      try {
        StreamToLoopReplacementContext context =
          new StreamToLoopReplacementContext(statement, operations, terminalCall, ct);
        registerVariables(operations, context);
        String replacement = "";
        for (OperationRecord or : StreamEx.ofReversed(operations)) {
          replacement = or.myOperation.wrap(or.myInVar, or.myOutVar, replacement, context);
        }
        PsiElement firstAdded = null;
        for (PsiStatement addedStatement : ((PsiBlockStatement)factory.createStatementFromText("{" + replacement + "}", statement))
          .getCodeBlock().getStatements()) {
          PsiElement res = addStatement(project, statement, addedStatement);
          if (firstAdded == null) {
            firstAdded = res;
          }
        }
        PsiElement result = context.makeFinalReplacement();
        if(result != null) {
          result = normalize(project, result);
          if (firstAdded == null) {
            firstAdded = result;
          }
        }
        if (firstAdded != null) {
          ct.insertCommentsBefore(firstAdded);
        }
      }
      catch (Exception ex) {
        String text = terminalCall.getText();
        LOG.error(LogMessageEx.createEvent("Error converting Stream to loop", ExceptionUtil.getThrowableText(ex),
                                           new Attachment("Stream_code.txt", text)));
      }
    }

    private static PsiElement addStatement(@NotNull Project project, PsiStatement statement, PsiStatement context) {
      PsiElement element = statement.getParent().addBefore(context, statement);
      return normalize(project, element);
    }

    private static PsiElement normalize(@NotNull Project project, PsiElement element) {
      element = JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(element);
      RedundantCastUtil.getRedundantCastsInside(element).forEach(RedundantCastUtil::removeCast);
      return element;
    }

    private static StreamEx<OperationRecord> allOperations(List<OperationRecord> operations) {
      return StreamEx.of(operations)
        .flatMap(or -> or.myOperation.nestedOperations().append(or));
    }

    private static void registerVariables(List<OperationRecord> operations, StreamToLoopReplacementContext context) {
      allOperations(operations).forEach(or -> or.myOperation.preprocessVariables(context, or.myInVar, or.myOutVar));
      allOperations(operations).map(or -> or.myOperation).forEach(op -> op.registerReusedElements(context::registerReusedElement));
      allOperations(operations).map(or -> or.myInVar).distinct().forEach(var -> var.register(context));
    }
  }

  enum ResultKind {
    /**
     * Result variable is used as complete stream result and not modified after declaration
     * E.g. {@code Collectors.toList()} creates such result.
     */
    FINAL,
    /**
     * Result variable is used as complete stream result, but could be modified in loop
     * E.g. {@code Stream.count()} creates such result.
     */
    NON_FINAL,
    /**
     * Result variable is not directly used as stream result: additional transformations are possible.
     */
    UNKNOWN
  }

  static class StreamToLoopReplacementContext {
    private final boolean myHasNestedLoops;
    private final String mySuffix;
    private final Set<String> myUsedNames;
    private final Set<String> myUsedLabels;
    private final List<String> myBeforeSteps = new ArrayList<>();
    private final List<String> myAfterSteps = new ArrayList<>();
    private final CommentTracker myCommentTracker;
    private PsiElement myStreamExpression;
    private final PsiElementFactory myFactory;
    private String myLabel;
    private String myFinisher;

    StreamToLoopReplacementContext(PsiStatement statement,
                                   List<OperationRecord> records,
                                   @NotNull PsiExpression streamExpression,
                                   CommentTracker ct) {
      myFactory = JavaPsiFacade.getElementFactory(streamExpression.getProject());
      myHasNestedLoops = records.stream().anyMatch(or -> or.myOperation instanceof FlatMapOperation);
      myStreamExpression = streamExpression;
      mySuffix = myHasNestedLoops ? "Outer" : "";
      myCommentTracker = ct;
      myUsedNames = new HashSet<>();
      myUsedLabels = StreamEx.iterate(statement, Objects::nonNull, PsiElement::getParent).select(PsiLabeledStatement.class)
        .map(PsiLabeledStatement::getName).toSet();
    }

    StreamToLoopReplacementContext(StreamToLoopReplacementContext parentContext, List<OperationRecord> records) {
      myUsedNames = parentContext.myUsedNames;
      myUsedLabels = parentContext.myUsedLabels;
      myStreamExpression = parentContext.myStreamExpression;
      myFactory = parentContext.myFactory;
      myCommentTracker = parentContext.myCommentTracker;
      myHasNestedLoops = records.stream().anyMatch(or -> or.myOperation instanceof FlatMapOperation);
      mySuffix = "Inner";
    }

    public void registerReusedElement(@Nullable PsiElement element) {
      if(element == null) return;
      element.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitVariable(PsiVariable variable) {
          super.visitVariable(variable);
          myUsedNames.add(variable.getName());
        }
      });
      myCommentTracker.markUnchanged(element);
    }

    @Nullable
    private String allocateLabel() {
      if(!myHasNestedLoops) return null;
      if(myLabel == null) {
        String base = mySuffix.toUpperCase(Locale.ENGLISH);
        myLabel = IntStreamEx.ints().mapToObj(i -> i == 0 ? base : base + i)
          .remove(myUsedLabels::contains).findFirst().orElseThrow(IllegalArgumentException::new);
        myUsedLabels.add(myLabel);
      }
      return myLabel;
    }

    public String getLoopLabel() {
      return myLabel == null ? "" : myLabel + ":\n";
    }

    public String getBreakStatement() {
      String label = allocateLabel();
      return label == null ? "break;\n" : "break "+label+";\n";
    }

    public String registerVarName(Collection<String> variants) {
      if(variants.isEmpty()) {
        return registerVarName(Collections.singleton("val"));
      }
      for(int idx = 0; ; idx++) {
        for(String variant : variants) {
          String name = idx == 0 ? variant : variant + idx;
          if(!isUsed(name)) {
            myUsedNames.add(name);
            return name;
          }
        }
      }
    }

    private boolean isUsed(String varName) {
      return myUsedNames.contains(varName) || JavaLexer.isKeyword(varName, LanguageLevel.HIGHEST) ||
             !varName.equals(JavaCodeStyleManager.getInstance(getProject())
                               .suggestUniqueVariableName(varName, myStreamExpression,
                                                          v -> PsiTreeUtil.isAncestor(myStreamExpression, v, true)));
    }

    public String declare(String desiredName, String type, String initializer) {
      String name = registerVarName(
        mySuffix.isEmpty() ? Collections.singleton(desiredName) : Arrays.asList(desiredName, desiredName + mySuffix));
      myBeforeSteps.add(type + " " + name + " = " + initializer + ";");
      return name;
    }

    public void addBeforeStep(String beforeStatement) {
      myBeforeSteps.add(beforeStatement);
    }

    public void addAfterStep(String afterStatement) {
      myAfterSteps.add(0, afterStatement);
    }

    public String drainAfterSteps() {
      String afterSteps = String.join("", myAfterSteps);
      myAfterSteps.clear();
      return afterSteps;
    }

    public String drainBeforeSteps() {
      String beforeSteps = String.join("", myBeforeSteps);
      myBeforeSteps.clear();
      return beforeSteps;
    }

    public String declareResult(String desiredName, PsiType type, String initializer, @NotNull ResultKind kind) {
      return declareResult(desiredName, type, null, initializer, kind);
    }

    public String declareResult(String desiredName,
                                PsiType type,
                                String mostAbstractAllowedType,
                                String initializer,
                                @NotNull ResultKind kind) {
      if (kind != ResultKind.UNKNOWN && myStreamExpression.getParent() instanceof PsiVariable) {
        PsiVariable var = (PsiVariable)myStreamExpression.getParent();
        if (isCompatibleType(var, type, mostAbstractAllowedType) &&
            var.getParent() instanceof PsiDeclarationStatement && (kind == ResultKind.FINAL || canUseAsNonFinal(var))) {
          PsiDeclarationStatement declaration = (PsiDeclarationStatement)var.getParent();
          if(declaration.getDeclaredElements().length == 1) {
            myStreamExpression = declaration;
            PsiVariable copy = (PsiVariable)var.copy();
            if (kind == ResultKind.NON_FINAL) {
              PsiModifierList modifierList = copy.getModifierList();
              if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.FINAL, false);
              }
            }
            PsiExpression oldInitializer = copy.getInitializer();
            LOG.assertTrue(oldInitializer != null);
            oldInitializer.replace(createExpression(initializer));
            myBeforeSteps.add(copy.getText());
            return var.getName();
          }
        }
      }
      String name = registerVarName(Arrays.asList(desiredName, "result"));
      myBeforeSteps.add(type.getCanonicalText() + " " + name + " = " + initializer + ";");
      if(myFinisher != null) {
        throw new IllegalStateException("Finisher is already defined");
      }
      setFinisher(name);
      return name;
    }

    private static boolean isCompatibleType(@NotNull PsiVariable var, @NotNull PsiType type, @Nullable String mostAbstractAllowedType) {
      if (EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(var.getType(), type)) return true;
      if (mostAbstractAllowedType == null) return false;
      PsiType[] superTypes = type.getSuperTypes();
      return Arrays.stream(superTypes).anyMatch(superType -> InheritanceUtil.isInheritor(superType, mostAbstractAllowedType) &&
                                                             isCompatibleType(var, superType, mostAbstractAllowedType));
    }

    @Contract("null -> false")
    private static boolean canUseAsNonFinal(PsiVariable var) {
      if (!(var instanceof PsiLocalVariable)) return false;
      PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
      return block != null && ReferencesSearch.search(var).forEach(ref -> {
        PsiElement context = PsiTreeUtil.getParentOfType(ref.getElement(), PsiClass.class, PsiLambdaExpression.class);
        return context == null || PsiTreeUtil.isAncestor(context, block, false);
      });
    }

    public PsiElement makeFinalReplacement() {
      LOG.assertTrue(myStreamExpression != null);
      if (myFinisher == null || myStreamExpression instanceof PsiStatement) {
        myCommentTracker.delete(myStreamExpression);
        return null;
      }
      else {
        PsiExpression expression = myFactory.createExpressionFromText(myFinisher, myStreamExpression);
        PsiElement parent = myStreamExpression.getParent();
        if (parent instanceof PsiExpression && ParenthesesUtils.areParenthesesNeeded(expression, (PsiExpression)parent, false)) {
          expression = myFactory.createExpressionFromText("("+myFinisher+")", myStreamExpression);
        }
        return myCommentTracker.replace(myStreamExpression, expression);
      }
    }

    public void setFinisher(String finisher) {
      myFinisher = finisher;
    }

    public void setFinisher(ConditionalExpression conditionalExpression) {
      if(conditionalExpression instanceof ConditionalExpression.Optional) {
        conditionalExpression = tryUnwrapOptional((ConditionalExpression.Optional)conditionalExpression, true);
      }
      setFinisher(conditionalExpression.asExpression());
    }

    public String assignAndBreak(ConditionalExpression conditionalExpression) {
      PsiStatement statement = PsiTreeUtil.getParentOfType(myStreamExpression, PsiStatement.class);
      boolean inReturn = statement instanceof PsiReturnStatement;
      if(conditionalExpression instanceof ConditionalExpression.Optional) {
        conditionalExpression = tryUnwrapOptional((ConditionalExpression.Optional)conditionalExpression, inReturn);
      }
      if (conditionalExpression instanceof ConditionalExpression.Boolean) {
        conditionalExpression = tryUnwrapBoolean((ConditionalExpression.Boolean)conditionalExpression, inReturn);
      }
      if (inReturn) {
        setFinisher(conditionalExpression.getFalseBranch());
        Object mark = new Object();
        PsiTreeUtil.mark(myStreamExpression, mark);
        PsiElement returnCopy = statement.copy();
        PsiElement placeHolderCopy = PsiTreeUtil.releaseMark(returnCopy, mark);
        LOG.assertTrue(placeHolderCopy != null);
        PsiElement replacement = placeHolderCopy.replace(createExpression(conditionalExpression.getTrueBranch()));
        return (placeHolderCopy == returnCopy ? replacement : returnCopy).getText();
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(myStreamExpression.getParent());
      if(parent instanceof PsiIfStatement && conditionalExpression instanceof ConditionalExpression.Boolean &&
         !((ConditionalExpression.Boolean)conditionalExpression).isInverted()) {
        PsiIfStatement ifStatement = (PsiIfStatement)parent;
        if(ifStatement.getElseBranch() == null) {
          PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
          if(thenStatement instanceof PsiReturnStatement || thenStatement instanceof PsiThrowStatement) {
            myStreamExpression = parent;
            return thenStatement.getText();
          }
          if(thenStatement instanceof PsiExpressionStatement) {
            myStreamExpression = parent;
            return thenStatement.getText() + "\n" + getBreakStatement();
          }
        }
      }
      if(conditionalExpression instanceof ConditionalExpression.Optional && myStreamExpression instanceof PsiExpression) {
        PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)myStreamExpression);
        if(call != null && call.getParent() instanceof PsiExpressionStatement) {
          PsiExpression[] args = call.getArgumentList().getExpressions();
          if(args.length == 1 && "ifPresent".equals(call.getMethodExpression().getReferenceName())) {
            FunctionHelper fn = FunctionHelper.create(args[0], 1);
            if(fn != null) {
              fn.transform(this, ((ConditionalExpression.Optional)conditionalExpression).unwrap("").getTrueBranch());
              myStreamExpression = call.getParent();
              return fn.getStatementText() + getBreakStatement();
            }
          }
        }
      }
      String found =
        declareResult(conditionalExpression.getCondition(), createType(conditionalExpression.getType()),
                      conditionalExpression.getFalseBranch(), ResultKind.NON_FINAL);
      return found + " = " + conditionalExpression.getTrueBranch() + ";\n" + getBreakStatement();
    }

    private ConditionalExpression tryUnwrapBoolean(ConditionalExpression.Boolean condition, boolean unwrapLazilyEvaluated) {
      if (myStreamExpression instanceof PsiExpression) {
        PsiExpression negation = BoolUtils.findNegation((PsiExpression)myStreamExpression);
        if (negation != null) {
          myStreamExpression = negation;
          condition = condition.negate();
        }

        PsiElement parent = PsiUtil.skipParenthesizedExprUp(myStreamExpression.getParent());
        ConditionalExpression candidate = null;
        if (parent instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression expression = (PsiPolyadicExpression)parent;
          PsiExpression[] operands = expression.getOperands();
          if (operands.length > 1 && PsiTreeUtil.isAncestor(operands[0], myStreamExpression, false)) {
            IElementType type = expression.getOperationTokenType();
            if (type.equals(JavaTokenType.ANDAND)) {
              candidate = condition
                .toPlain(PsiType.BOOLEAN, StreamEx.of(operands, 1, operands.length).map(PsiExpression::getText).joining(" && "), "false");
            } else if (type.equals(JavaTokenType.OROR)) {
              candidate = condition
                .toPlain(PsiType.BOOLEAN, "true", StreamEx.of(operands, 1, operands.length).map(PsiExpression::getText).joining(" || "));
            }
          }
        } else if (parent instanceof PsiConditionalExpression) {
          PsiConditionalExpression ternary = (PsiConditionalExpression)parent;
          if (PsiTreeUtil.isAncestor(ternary.getCondition(), myStreamExpression, false)) {
            PsiType type = ternary.getType();
            PsiExpression thenExpression = ternary.getThenExpression();
            PsiExpression elseExpression = ternary.getElseExpression();
            if (type != null && thenExpression != null && elseExpression != null) {
              candidate = condition.toPlain(type, thenExpression.getText(), elseExpression.getText());
            }
          }
        }
        if (candidate != null &&
            (unwrapLazilyEvaluated || ExpressionUtils.isSimpleExpression(createExpression(candidate.getFalseBranch())))) {
          myStreamExpression = parent;
          return candidate;
        }
      }
      return condition;
    }

    @NotNull
    private ConditionalExpression tryUnwrapOptional(ConditionalExpression.Optional condition, boolean unwrapLazilyEvaluated) {
      if (myStreamExpression instanceof PsiExpression) {
        PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)myStreamExpression);
        if (call != null && !(call.getParent() instanceof PsiExpressionStatement)) {
          String name = call.getMethodExpression().getReferenceName();
          PsiExpression[] args = call.getArgumentList().getExpressions();
          if (args.length == 0 && "isPresent".equals(name)) {
            myStreamExpression = call;
            return new ConditionalExpression.Boolean(condition.getCondition(), false);
          }
          if (args.length == 1) {
            String absentExpression = null;
            if ("orElse".equals(name)) {
              absentExpression = args[0].getText();
            }
            else if (unwrapLazilyEvaluated && "orElseGet".equals(name)) {
              FunctionHelper helper = FunctionHelper.create(args[0], 0);
              if (helper != null) {
                helper.transform(this);
                absentExpression = helper.getText();
              }
            }
            if (absentExpression != null) {
              myStreamExpression = call;
              return condition.unwrap(absentExpression);
            }
          }
        }
      }
      return condition;
    }

    public Project getProject() {
      return myStreamExpression.getProject();
    }

    public PsiExpression createExpression(String text) {
      return myFactory.createExpressionFromText(text, myStreamExpression);
    }

    public PsiStatement createStatement(String text) {
      return myFactory.createStatementFromText(text, myStreamExpression);
    }

    public PsiType createType(String text) {
      return myFactory.createTypeFromText(text, myStreamExpression);
    }
  }

  static class OperationRecord {
    Operation myOperation;
    StreamVariable myInVar, myOutVar;
  }
}
