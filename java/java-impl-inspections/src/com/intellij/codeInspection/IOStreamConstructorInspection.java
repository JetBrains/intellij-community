// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class IOStreamConstructorInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel7OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression newExpression) {
        super.visitNewExpression(newExpression);
        IOStreamConstructorModel constructorModel = IOStreamConstructorModel.create(newExpression);
        if (constructorModel == null) return;
        StreamType streamType = constructorModel.myStreamType;
        if (!canSubstituteBaseIOStream(newExpression, streamType)) return;
        ReplaceWithNioCallFix fix = new ReplaceWithNioCallFix(streamType.myReplacement, isOnTheFly);
        holder.registerProblem(newExpression, JavaBundle.message(streamType.myErrorText), fix);
      }
    };
  }

  private static boolean canSubstituteBaseIOStream(@NotNull PsiNewExpression newExpression, StreamType streamType) {
    PsiExpression lastExpression = newExpression;
    while (lastExpression.getParent() instanceof PsiParenthesizedExpression) {
      lastExpression = (PsiExpression)lastExpression.getParent();
    }
    PsiParameter parameter = MethodCallUtils.getParameterForArgument(lastExpression);
    if (parameter != null) {
      return TypeConversionUtil.isAssignable(parameter.getType(), streamType.baseType(newExpression));
    }
    PsiElement parent = lastExpression.getParent();
    PsiAssignmentExpression assignment = ObjectUtils.tryCast(parent, PsiAssignmentExpression.class);
    if (assignment != null) {
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null || !PsiTreeUtil.isAncestor(rhs, lastExpression, false)) return false;
      PsiType lType = assignment.getLExpression().getType();
      return lType != null && TypeConversionUtil.isAssignable(lType, streamType.baseType(newExpression));
    }
    PsiConditionalExpression conditional = ObjectUtils.tryCast(parent, PsiConditionalExpression.class);
    if (conditional != null) {
      PsiType conditionalType = conditional.getType();
      return conditionalType != null && TypeConversionUtil.isAssignable(conditionalType, streamType.baseType(newExpression));
    }
    PsiVariable variable = ObjectUtils.tryCast(parent, PsiVariable.class);
    if (variable != null) {
      return TypeConversionUtil.isAssignable(variable.getType(), streamType.baseType(newExpression));
    }
    return false;
  }

  private static @Nullable PsiExpression getOnlyArgument(@NotNull PsiCallExpression expression) {
    PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList == null) return null;
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 1) return null;
    return ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(arguments[0]), PsiExpression.class);
  }

  private enum StreamType {
    INPUT_STREAM(CommonClassNames.JAVA_IO_FILE_INPUT_STREAM,
                 "java.io.InputStream", "inspection.input.stream.constructor.message",
                 "Files.newInputStream"),
    OUTPUT_STREAM(CommonClassNames.JAVA_IO_FILE_OUTPUT_STREAM,
                  "java.io.OutputStream", "inspection.output.stream.constructor.message",
                  "Files.newOutputStream");

    private final String myTypeText;
    private final String myBaseTypeText;
    private final String myErrorText;
    private final String myReplacement;

    StreamType(String typeText, String baseTypeText, String errorText, String replacement) {
      myTypeText = typeText;
      myBaseTypeText = baseTypeText;
      myErrorText = errorText;
      myReplacement = replacement;
    }

    public @NotNull PsiType baseType(@NotNull PsiElement context) {
      PsiElementFactory factory = PsiElementFactory.getInstance(context.getProject());
      return factory.createTypeFromText(myBaseTypeText, context);
    }
  }

  private interface ArgumentModel {

    String createReplacement();

    boolean isEffectivelyFinal(@NotNull PsiElement context);

    private static @Nullable ArgumentModel create(@NotNull PsiExpression argument) {
      PsiType argType = argument.getType();
      if (argType == null) return null;
      if (argType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        PsiReferenceExpression stringRef = ObjectUtils.tryCast(argument, PsiReferenceExpression.class);
        if (stringRef != null) {
          return isLocalVarRef(stringRef) ? new StringExpr(stringRef) : null;
        }
        PsiLiteralExpression literal = ObjectUtils.tryCast(argument, PsiLiteralExpression.class);
        return literal == null ? null : new StringExpr(literal);
      }
      if (argType.equalsToText(CommonClassNames.JAVA_IO_FILE)) {
        PsiReferenceExpression fileRef = ObjectUtils.tryCast(argument, PsiReferenceExpression.class);
        if (fileRef != null) {
          return isLocalVarRef(fileRef) ? new FileRef(fileRef) : null; 
        }
      }
      return null;
    }
    
    private static boolean isLocalVarRef(@NotNull PsiReferenceExpression ref) {
      PsiVariable variable = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
      return variable != null && !(variable instanceof PsiField); 
    }

    class FileRef implements ArgumentModel {

      private final PsiReferenceExpression myFileRef;

      public FileRef(@NotNull PsiReferenceExpression fileRef) {
        myFileRef = fileRef;
      }

      @Override
      public String createReplacement() {
        return myFileRef.getText() + ".toPath()";
      }

      @Override
      public boolean isEffectivelyFinal(@NotNull PsiElement context) {
        PsiVariable psiVariable = ObjectUtils.tryCast(myFileRef.resolve(), PsiVariable.class);
        if (psiVariable == null) return false;
        return HighlightControlFlowUtil.isEffectivelyFinal(psiVariable, context, null);
      }
    }

    class StringExpr implements ArgumentModel {
      private final PsiExpression myStringExpr;

      public StringExpr(@NotNull PsiExpression stringExpr) {
        myStringExpr = stringExpr;
      }

      @Override
      public String createReplacement() {
        return "java.nio.file.Path.of(" + myStringExpr.getText() + ")";
      }

      @Override
      public boolean isEffectivelyFinal(@NotNull PsiElement context) {
        PsiLiteralExpression literal = ObjectUtils.tryCast(myStringExpr, PsiLiteralExpression.class);
        if (literal != null) return true;
        PsiReferenceExpression stringRef = ObjectUtils.tryCast(myStringExpr, PsiReferenceExpression.class);
        if (stringRef == null) return false;
        PsiVariable psiVariable = ObjectUtils.tryCast(stringRef.resolve(), PsiVariable.class);
        if (psiVariable == null) return false;
        return HighlightControlFlowUtil.isEffectivelyFinal(psiVariable, context, null);
      }
    }
  }

  private static class IOStreamConstructorModel {

    private final StreamType myStreamType;
    private final ArgumentModel myArgument;

    private IOStreamConstructorModel(@NotNull StreamType type, @NotNull ArgumentModel argument) {
      myStreamType = type;
      myArgument = argument;
    }

    private static @Nullable IOStreamConstructorModel create(@NotNull PsiNewExpression expression) {
      PsiType expressionType = expression.getType();
      if (expressionType == null) return null;
      StreamType streamType = ContainerUtil.find(StreamType.values(), t -> expressionType.equalsToText(t.myTypeText));
      if (streamType == null) return null;
      PsiExpression argument = getOnlyArgument(expression);
      if (argument == null) return null;
      ArgumentModel argModel = ArgumentModel.create(argument);
      if (argModel == null) return null;
      return new IOStreamConstructorModel(streamType, argModel);
    }
  }

  private static class ReplaceWithNioCallFix implements LocalQuickFix {

    private final String myReplacement;
    private final boolean myIsOnTheFly;

    private ReplaceWithNioCallFix(String replacement, boolean isOnTheFly) {
      myReplacement = replacement;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacement);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiNewExpression newExpression = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiNewExpression.class);
      if (newExpression == null) return;
      IOStreamConstructorModel constructorModel = IOStreamConstructorModel.create(newExpression);
      if (constructorModel == null) return;
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(newExpression, PsiMethod.class);
      if (containingMethod == null) return;
      boolean isEffectivelyFinal = constructorModel.myArgument.isEffectivelyFinal(containingMethod);
      String filesCallText = "java.nio.file." + constructorModel.myStreamType.myReplacement + 
                             "(" + constructorModel.myArgument.createReplacement() + ")";
      PsiElement result = PsiReplacementUtil.replaceExpressionAndShorten(newExpression, filesCallText, new CommentTracker());
      if (!isEffectivelyFinal) return;
      PsiMethodCallExpression filesMethodCall = ObjectUtils.tryCast(result, PsiMethodCallExpression.class);
      if (filesMethodCall == null) return;
      PsiMethodCallExpression toPathConversion = ObjectUtils.tryCast(getOnlyArgument(filesMethodCall), PsiMethodCallExpression.class);
      if (toPathConversion == null) return;
      PsiExpression[] occurrences = CodeInsightUtil.findExpressionOccurrences(containingMethod, toPathConversion);
      if (occurrences.length < 2) return;
      // maybe we can reuse already created file.toPath() / Path.of(...) variable
      List<PsiLocalVariable> pathVars = Arrays.stream(occurrences)
        .map(o -> ObjectUtils.tryCast(o.getParent(), PsiLocalVariable.class))
        .filter(var -> var != null && HighlightControlFlowUtil.isEffectivelyFinal(var, containingMethod, null))
        .filter(var -> var.getParent() instanceof PsiDeclarationStatement)
        .collect(Collectors.toList());
      if (!pathVars.isEmpty()) {
        PsiCodeBlock body = containingMethod.getBody();
        if (body == null) return;
        ControlFlow flow = createControlFlow(body);
        if (flow == null) return;
        int conversionOffset = flow.getStartOffset(toPathConversion);
        if (conversionOffset == -1) return;
        Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, conversionOffset, true);
        PsiLocalVariable pathVar = ContainerUtil.find(pathVars, var -> writtenVariables.contains(var));
        if (pathVar != null) {
          PsiReplacementUtil.replaceExpressionAndShorten(toPathConversion, pathVar.getName(), new CommentTracker());
          return;
        }
      }
      if (!myIsOnTheFly) return;
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) return;
      JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
      IntroduceVariableHandler handler = ObjectUtils.tryCast(factory.createIntroduceVariableHandler(), IntroduceVariableHandler.class);
      if (handler != null) handler.invoke(project, editor, toPathConversion);
    }

    @Nullable
    private static ControlFlow createControlFlow(@NotNull PsiCodeBlock block) {
      try {
        LocalsOrMyInstanceFieldsControlFlowPolicy flowPolicy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
        return ControlFlowFactory.getInstance(block.getProject()).getControlFlow(block, flowPolicy);
      }
      catch (AnalysisCanceledException ignored) {
        return null;
      }
    }
  }
}
