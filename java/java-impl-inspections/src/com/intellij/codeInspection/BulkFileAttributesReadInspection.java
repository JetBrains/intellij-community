// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BulkFileAttributesReadInspection extends AbstractBaseJavaLocalInspectionTool {
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
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        if (!FILE_ATTR_CALL_MATCHER.test(call)) return;
        PsiVariable fileVariable = getFileVariable(call);
        if (fileVariable == null) return;
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
        if (containingMethod == null) return;
        if (!HighlightControlFlowUtil.isEffectivelyFinal(fileVariable, containingMethod, null)) return;
        List<PsiMethodCallExpression> attrCalls = findAttributeCalls(fileVariable, containingMethod);
        if (distinctCalls(attrCalls) < 2) return;
        if (!isOnTheFly) {
          PsiExpression[] occurrences = attrCalls.toArray(PsiExpression.EMPTY_ARRAY);
          PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, containingMethod);
          if (anchor == null || needsTryCatchBlock(anchor)) return;
        }
        holder.registerProblem(call, JavaBundle.message("inspection.bulk.file.attributes.read.message"),
                               new ReplaceWithBulkCallFix(isOnTheFly));
      }
    };
  }

  @Nullable
  private static PsiVariable getFileVariable(@NotNull PsiMethodCallExpression call) {
    PsiReferenceExpression qualifier = ObjectUtils.tryCast(call.getMethodExpression().getQualifier(), PsiReferenceExpression.class);
    if (qualifier == null) return null;
    return ObjectUtils.tryCast(qualifier.resolve(), PsiVariable.class);
  }

  @NotNull
  private static List<PsiMethodCallExpression> findAttributeCalls(@NotNull PsiVariable fileVar, @NotNull PsiMethod containingMethod) {
    Collection<PsiMethodCallExpression> calls = ReferencesSearch.search(fileVar, new LocalSearchScope(containingMethod))
      .mapping(ref -> ObjectUtils.tryCast(ref, PsiReferenceExpression.class))
      .mapping(ref -> ref == null ? null : ExpressionUtils.getCallForQualifier(ref))
      .filtering(FILE_ATTR_CALL_MATCHER)
      .findAll();
    return new ArrayList<>(calls);
  }

  private static long distinctCalls(@NotNull List<PsiMethodCallExpression> calls) {
    return StreamEx.of(calls).distinct(call -> call.getMethodExpression().getReferenceName()).count();
  }

  private static boolean needsTryCatchBlock(@NotNull PsiElement anchor) {
    PsiClassType ioExceptionType = TypeUtils.getType("java.io.IOException", anchor);
    return !ExceptionUtil.isHandled(ioExceptionType, anchor);
  }

  private static class ReplaceWithBulkCallFix implements LocalQuickFix {

    private final boolean myIsOnTheFly;

    private ReplaceWithBulkCallFix(boolean isOnTheFly) {
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.bulk.file.attributes.read.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (!FILE_ATTR_CALL_MATCHER.test(call)) return;
      FileVariableModel fileVariable = FileVariableModel.create(call);
      if (fileVariable == null) return;
      PsiElement anchor = fileVariable.findAnchor();
      if (anchor == null) return;
      PsiElement parent = anchor.getParent();
      if (parent == null) return;
      String fileVarName = fileVariable.getName();
      if (fileVarName == null) return;
      AttributesVariableModel attributesVariable = AttributesVariableModel.create(fileVarName, fileVariable.myContainingMethod, anchor);
      if (attributesVariable == null) return;

      final PsiDeclarationStatement declaration;
      PsiReference[] usages = new PsiReference[fileVariable.myAttributeCalls.size() + (attributesVariable.myNeedsTryCatch ? 1 : 0)];
      if (!attributesVariable.myNeedsTryCatch) {
        declaration =
          addDeclaration(parent, anchor, attributesVariable.myName, attributesVariable.myType, attributesVariable.myInitializer);
      }
      else {
        declaration = addDeclaration(parent, anchor, attributesVariable.myName, attributesVariable.myType, null);
        PsiExpressionStatement assignment = addAssignment(parent, anchor, attributesVariable.myName, attributesVariable.myInitializer);
        assignment = surroundWithTryCatch(parent, declaration, assignment);
        if (assignment == null) return;
        PsiExpression lhs = getLhs(assignment);
        if (lhs == null) return;
        PsiReference lhsRef = lhs.getReference();
        if (lhsRef == null) return;
        usages[usages.length - 1] = lhsRef;
      }

      List<PsiMethodCallExpression> attrCalls = fileVariable.myAttributeCalls;
      for (int i = 0; i < attrCalls.size(); i++) {
        PsiMethodCallExpression attrCall = attrCalls.get(i);
        String replacement = getBulkCallReplacement(attributesVariable.myName, attrCall);
        attrCall = (PsiMethodCallExpression)PsiReplacementUtil.replaceExpressionAndShorten(attrCall, replacement, new CommentTracker());
        usages[i] = getTopLevelQualifier(attrCall).getReference();
      }

      if (!myIsOnTheFly) return;
      PsiVariable attrsVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      HighlightUtils.showRenameTemplate(fileVariable.myContainingMethod, attrsVariable, usages);
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

    private static @Nullable PsiExpressionStatement surroundWithTryCatch(@NotNull PsiElement parent,
                                                                         @NotNull PsiStatement prevStatement,
                                                                         @NotNull PsiExpressionStatement assignment) {
      JavaWithTryCatchSurrounder tryCatchSurrounder = new JavaWithTryCatchSurrounder();
      Project project = assignment.getProject();
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) return null;
      tryCatchSurrounder.surroundStatements(project, editor, parent, new PsiExpressionStatement[]{assignment});
      PsiTryStatement tryStatement = ObjectUtils.tryCast(prevStatement.getNextSibling(), PsiTryStatement.class);
      if (tryStatement == null) return null;
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
      PsiDeclarationStatement declaration = elementFactory.createVariableDeclarationStatement(varName, type, initializer);
      declaration = (PsiDeclarationStatement)parent.addBefore(declaration, anchor);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(parent.getProject());
      return (PsiDeclarationStatement)codeStyleManager.shortenClassReferences(declaration);
    }

    private static @NotNull String getBulkCallReplacement(@NotNull String attrsVarName, @NotNull PsiMethodCallExpression attrCall) {
      String attrMethodName = Objects.requireNonNull(attrCall.getMethodExpression().getReferenceName());
      return attrsVarName + "." + ATTR_REPLACEMENTS.get(attrMethodName) + "()";
    }

    private static @NotNull PsiElement getTopLevelQualifier(@NotNull PsiMethodCallExpression methodCall) {
      PsiElement qualifier = PsiUtil.skipParenthesizedExprUp(methodCall.getMethodExpression().getQualifier());
      while (qualifier instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)qualifier;
        qualifier = PsiUtil.skipParenthesizedExprUp(call.getMethodExpression().getQualifier());
      }
      return Objects.requireNonNull(qualifier);
    }

    private static class FileVariableModel {
      private final PsiVariable myFileVariable;
      private final PsiMethod myContainingMethod;
      private List<PsiMethodCallExpression> myAttributeCalls;

      private FileVariableModel(@NotNull List<PsiMethodCallExpression> attributeCalls,
                                @NotNull PsiVariable fileVariable, @NotNull PsiMethod containingMethod) {
        myAttributeCalls = attributeCalls;
        myFileVariable = fileVariable;
        myContainingMethod = containingMethod;
      }

      private @Nullable String getName() {
        return myFileVariable.getName();
      }

      private @Nullable PsiElement findAnchor() {
        PsiExpression[] occurrences = myAttributeCalls.toArray(PsiExpression.EMPTY_ARRAY);
        PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, myContainingMethod);
        if (anchor == null) return null;
        PsiLambdaExpression lambda = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(anchor.getParent()), PsiLambdaExpression.class);
        if (lambda == null) return anchor;
        PsiCodeBlock codeBlock = RefactoringUtil.expandExpressionLambdaToCodeBlock(lambda);
        // attribute calls were inside lambda, need to recalculate them
        myAttributeCalls = findAttributeCalls(myFileVariable, myContainingMethod);
        return ControlFlowUtils.getOnlyStatementInBlock(codeBlock);
      }

      private static FileVariableModel create(@NotNull PsiMethodCallExpression variableUsage) {
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(variableUsage, PsiMethod.class);
        if (containingMethod == null) return null;
        PsiVariable psiVariable = getFileVariable(variableUsage);
        if (psiVariable == null) return null;
        List<PsiMethodCallExpression> attributeCalls = findAttributeCalls(psiVariable, containingMethod);
        if (distinctCalls(attributeCalls) < 2) return null;
        return new FileVariableModel(attributeCalls, psiVariable, containingMethod);
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
