// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class BulkFileAttributesReadInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Map<String, String> ATTR_REPLACEMENTS = Map.of(
    "lastModified", "lastModifiedTime().toMillis",
    "isFile", "isRegularFile",
    "isDirectory", "isDirectory",
    "length", "size"
  );

  private static final CallMatcher FILE_ATTR_CALL_MATCHER =
    CallMatcher.instanceCall(CommonClassNames.JAVA_IO_FILE, ArrayUtil.toStringArray(ATTR_REPLACEMENTS.keySet())).parameterCount(0);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel7OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        super.visitMethod(method);
        PsiCodeBlock methodBody = method.getBody();
        if (methodBody == null) return;
        CallReporter reporter = new CallReporter(methodBody, isOnTheFly, holder);
        FileAttributeCallsVisitor visitor = new FileAttributeCallsVisitor(methodBody, reporter);
        methodBody.accept(visitor);
        reporter.accept(visitor.myCalls);
      }
    };
  }

  private static long distinctCalls(@NotNull List<? extends PsiMethodCallExpression> calls) {
    return StreamEx.of(calls).distinct(call -> call.getMethodExpression().getReferenceName()).count();
  }

  private static boolean needsTryCatchBlock(@NotNull PsiElement anchor) {
    PsiClassType ioExceptionType = TypeUtils.getType("java.io.IOException", anchor);
    return !ExceptionUtil.isHandled(ioExceptionType, anchor);
  }

  private static @Nullable PsiVariable getFileVariable(@NotNull PsiMethodCallExpression call) {
    PsiElement qualifier = call.getMethodExpression().getQualifier();
    if (qualifier instanceof PsiParenthesizedExpression) {
      qualifier = PsiUtil.skipParenthesizedExprDown((PsiExpression)qualifier);
    }
    PsiReferenceExpression ref = ObjectUtils.tryCast(qualifier, PsiReferenceExpression.class);
    if (ref == null) return null;
    return ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
  }

  private static class CallReporter implements Consumer<Map<PsiVariable, List<PsiMethodCallExpression>>> {

    private final PsiElement myScope;
    private final boolean myIsOnTheFly;
    private final ProblemsHolder myHolder;

    private CallReporter(@NotNull PsiElement scope, boolean isOnTheFly, @NotNull ProblemsHolder holder) {
      myScope = scope;
      myIsOnTheFly = isOnTheFly;
      myHolder = holder;
    }

    @Override
    public void accept(Map<PsiVariable, List<PsiMethodCallExpression>> calls) {
      calls.forEach((variable, varCalls) -> {
        if (distinctCalls(varCalls) < 2) return;
        PsiExpression[] occurrences = varCalls.toArray(PsiExpression.EMPTY_ARRAY);
        PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, myScope);
        if (anchor == null) return;
        boolean needsTryCatchBlock = needsTryCatchBlock(anchor);
        if (!needsTryCatchBlock) {
          varCalls.forEach(call -> myHolder.registerProblem(call, JavaBundle.message("inspection.bulk.file.attributes.read.message"),
                                                            new ReplaceWithBulkCallFix()));
          return;
        }
        if (myIsOnTheFly) {
          varCalls.forEach(call -> myHolder.registerProblem(call, JavaBundle.message("inspection.bulk.file.attributes.read.message"),
                                                            ProblemHighlightType.INFORMATION,
                                                            new ReplaceWithBulkCallFix()));
        }
      });
    }
  }

  private static class FileAttributeCallsVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiElement myScope;
    private final Map<PsiVariable, List<PsiMethodCallExpression>> myCalls = new HashMap<>();
    private final Consumer<? super Map<PsiVariable, List<PsiMethodCallExpression>>> myReporter;

    private FileAttributeCallsVisitor(@NotNull PsiElement scope,
                                      @NotNull Consumer<? super Map<PsiVariable, List<PsiMethodCallExpression>>> reporter) {
      myScope = scope;
      myReporter = reporter;
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      doVisitLoop(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      doVisitLoop(statement);
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      doVisitLoop(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      doVisitLoop(statement);
    }

    private void doVisitLoop(@NotNull PsiLoopStatement loop) {
      PsiStatement loopBody = loop.getBody();
      if (loopBody == null) return;
      FileAttributeCallsVisitor visitor = new FileAttributeCallsVisitor(myScope, myReporter);
      loopBody.accept(visitor);
      myReporter.accept(visitor.myCalls);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (!FILE_ATTR_CALL_MATCHER.test(call)) return;
      PsiVariable variable = getFileVariable(call);
      if (variable == null || !ControlFlowUtil.isEffectivelyFinal(variable, myScope)) return;
      List<PsiMethodCallExpression> varCalls = myCalls.computeIfAbsent(variable, __ -> new SmartList<>());
      varCalls.add(call);
    }
  }

  private static class ReplaceWithBulkCallFix extends PsiUpdateModCommandQuickFix {
    private ReplaceWithBulkCallFix() {
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.bulk.file.attributes.read.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
      if (!FILE_ATTR_CALL_MATCHER.test(call)) return;
      FileVariableModel fileVariable = FileVariableModel.create(call);
      if (fileVariable == null) return;
      PsiElement anchor = fileVariable.findAnchor();
      if (anchor == null) return;
      PsiElement parent = anchor.getParent();
      if (parent == null) return;
      String fileVarName = fileVariable.getName();
      if (fileVarName == null) return;
      AttributesVariableModel attributesVariable = AttributesVariableModel.create(fileVarName, fileVariable.myScope, anchor);
      if (attributesVariable == null) return;
      List<String> names =
        new VariableNameGenerator(anchor, VariableKind.LOCAL_VARIABLE).byType(attributesVariable.myType).byName(attributesVariable.myName)
          .generateAll(true);
      String name = names.get(0);

      final PsiDeclarationStatement declaration;
      if (!attributesVariable.myNeedsTryCatch) {
        declaration = addDeclaration(parent, anchor, name, attributesVariable.myType, attributesVariable.myInitializer);
      }
      else {
        declaration = addDeclaration(parent, anchor, name, attributesVariable.myType, null);
        PsiExpressionStatement assignment = addAssignment(parent, anchor, name, attributesVariable.myInitializer);
        assignment = surroundWithTryCatch(assignment);
        if (assignment == null) return;
        PsiExpression lhs = getLhs(assignment);
        if (lhs == null) return;
        PsiReference lhsRef = lhs.getReference();
        if (lhsRef == null) return;
      }

      List<PsiMethodCallExpression> attrCalls = fileVariable.myAttributeCalls;
      for (int i = 0; i < attrCalls.size(); i++) {
        PsiMethodCallExpression attrCall = attrCalls.get(i);
        String replacement = getBulkCallReplacement(name, attrCall);
        PsiReplacementUtil.replaceExpressionAndShorten(attrCall, replacement, new CommentTracker());
      }

      PsiVariable attrsVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      updater.rename(attrsVariable, names);
    }

    private static @NotNull PsiExpressionStatement addAssignment(@NotNull PsiElement parent,
                                                                 @NotNull PsiElement anchor,
                                                                 @NotNull String varName,
                                                                 @NotNull PsiExpression initializer) {
      PsiElementFactory elementFactory = PsiElementFactory.getInstance(parent.getProject());
      String assignmentText = varName + "=" + initializer.getText() + ";";
      PsiExpressionStatement assignment = (PsiExpressionStatement)elementFactory.createStatementFromText(assignmentText, parent);
      assignment = (PsiExpressionStatement)parent.addBefore(assignment, anchor);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(parent.getProject());
      return (PsiExpressionStatement)codeStyleManager.shortenClassReferences(assignment);
    }

    private static @Nullable PsiExpressionStatement surroundWithTryCatch(@NotNull PsiExpressionStatement assignment) {
      String tryCatchText = "try{" + assignment.getText() + "}" +
                            "catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}";
      PsiTryStatement tryStatement =
        (PsiTryStatement)PsiReplacementUtil.replaceStatementAndShortenClassNames(assignment, tryCatchText, new CommentTracker());
      return ObjectUtils.tryCast(ControlFlowUtils.getOnlyStatementInBlock(tryStatement.getTryBlock()), PsiExpressionStatement.class);
    }

    private static @Nullable PsiExpression getLhs(@NotNull PsiExpressionStatement statement) {
      PsiAssignmentExpression assignmentExpression = ObjectUtils.tryCast(statement.getExpression(), PsiAssignmentExpression.class);
      if (assignmentExpression == null) return null;
      return assignmentExpression.getLExpression();
    }

    private static @NotNull PsiDeclarationStatement addDeclaration(@NotNull PsiElement parent,
                                                                   @NotNull PsiElement anchor,
                                                                   @NotNull String varName,
                                                                   @NotNull PsiType type,
                                                                   @Nullable PsiExpression initializer) {
      PsiElementFactory elementFactory = PsiElementFactory.getInstance(parent.getProject());
      PsiType displayType;
      if (
        initializer != null
        && parent.getContext() != null
        && PsiUtil.isAvailable(JavaFeature.LVTI, parent)
        && JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE
      ) {
        displayType = TypeUtils.getType(JavaKeywords.VAR, parent.getContext());
      } else {
        displayType = type;
      }
      PsiDeclarationStatement declaration = elementFactory
        .createVariableDeclarationStatement(varName, displayType, initializer);
      declaration = (PsiDeclarationStatement)parent.addBefore(declaration, anchor);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(parent.getProject());
      return (PsiDeclarationStatement)codeStyleManager.shortenClassReferences(declaration);
    }

    private static @NotNull String getBulkCallReplacement(@NotNull String attrsVarName, @NotNull PsiMethodCallExpression attrCall) {
      String attrMethodName = Objects.requireNonNull(attrCall.getMethodExpression().getReferenceName());
      return attrsVarName + "." + ATTR_REPLACEMENTS.get(attrMethodName) + "()";
    }

    private static class FileVariableModel {
      private final PsiVariable myFileVariable;
      private final PsiElement myScope;
      private List<PsiMethodCallExpression> myAttributeCalls;

      private FileVariableModel(@NotNull List<PsiMethodCallExpression> attributeCalls,
                                @NotNull PsiVariable fileVariable, @NotNull PsiElement scope) {
        myAttributeCalls = attributeCalls;
        myFileVariable = fileVariable;
        myScope = scope;
      }

      private @Nullable String getName() {
        return myFileVariable.getName();
      }

      private @Nullable PsiElement findAnchor() {
        PsiExpression[] occurrences = myAttributeCalls.toArray(PsiExpression.EMPTY_ARRAY);
        PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, myScope);
        if (anchor == null) return null;
        PsiLambdaExpression lambda = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(anchor.getParent()), PsiLambdaExpression.class);
        if (lambda == null) return anchor;
        PsiCodeBlock codeBlock = CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock(lambda);
        // attribute calls were inside lambda, need to recalculate them
        myAttributeCalls = findAttributeCalls(myFileVariable, myScope);
        return ControlFlowUtils.getOnlyStatementInBlock(codeBlock);
      }

      private static @NotNull List<PsiMethodCallExpression> findAttributeCalls(@NotNull PsiVariable fileVar, @NotNull PsiElement scope) {
        Collection<PsiMethodCallExpression> calls = ReferencesSearch.search(fileVar, new LocalSearchScope(scope))
          .filtering(ref -> scope == findScope(ref.getElement()))
          .mapping(ref -> ObjectUtils.tryCast(ref, PsiReferenceExpression.class))
          .mapping(ref -> ref == null ? null : ExpressionUtils.getCallForQualifier(ref))
          .filtering(FILE_ATTR_CALL_MATCHER)
          .findAll();
        return new ArrayList<>(calls);
      }

      private static @Nullable FileVariableModel create(@NotNull PsiMethodCallExpression variableUsage) {
        PsiElement scope = findScope(variableUsage);
        if (scope == null) return null;
        PsiVariable psiVariable = getFileVariable(variableUsage);
        if (psiVariable == null) return null;
        List<PsiMethodCallExpression> attributeCalls = findAttributeCalls(psiVariable, scope);
        if (distinctCalls(attributeCalls) < 2) return null;
        return new FileVariableModel(attributeCalls, psiVariable, scope);
      }

      private static @Nullable PsiElement findScope(@NotNull PsiElement element) {
        PsiElement scope = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLoopStatement.class);
        if (scope instanceof PsiLoopStatement) scope = ((PsiLoopStatement)scope).getBody();
        return scope;
      }
    }

    private static class AttributesVariableModel {
      private final PsiType myType;
      private final String myName;
      private final PsiExpression myInitializer;
      private final boolean myNeedsTryCatch;

      private AttributesVariableModel(@NotNull PsiType type, @NotNull String name, @NotNull PsiExpression initializer,
                                      boolean needsTryCatch) {
        myType = type;
        myName = name;
        myInitializer = initializer;
        myNeedsTryCatch = needsTryCatch;
      }

      private static @Nullable AttributesVariableModel create(@NotNull String fileVarName,
                                                              @NotNull PsiElement context, @NotNull PsiElement anchor) {
        PsiClassType psiType = TypeUtils.getType("java.nio.file.attribute.BasicFileAttributes", context);
        PsiExpression initializer = createInitializer(context, psiType, fileVarName);
        String name = getSuggestedName(psiType, initializer, anchor);
        if (name == null) return null;
        boolean needsTryCatch = needsTryCatchBlock(anchor);
        return new AttributesVariableModel(psiType, name, initializer, needsTryCatch);
      }

      private static @NotNull PsiExpression createInitializer(@NotNull PsiElement context,
                                                              @NotNull PsiType psiType,
                                                              @NotNull String fileVarName) {
        String initializerText = "java.nio.file.Files.readAttributes(" +
                                 fileVarName + ".toPath()" + "," +
                                 psiType.getCanonicalText() + ".class" + ")";
        return PsiElementFactory.getInstance(context.getProject()).createExpressionFromText(initializerText, context);
      }

      private static @Nullable String getSuggestedName(@NotNull PsiType type,
                                                       @NotNull PsiExpression initializer, @NotNull PsiElement anchor) {
        SuggestedNameInfo nameInfo = CommonJavaRefactoringUtil.getSuggestedName(type, initializer, anchor);
        String[] names = nameInfo.names;
        return names.length == 0 ? null : names[0];
      }
    }
  }
}
