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
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
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
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(newExpression, false);
        if (expectedType == null) return;
        boolean canUseBaseType = TypeConversionUtil.isAssignable(expectedType, streamType.baseType(newExpression));
        if (!canUseBaseType) return;
        boolean isInfoLevel = PsiUtil.isLanguageLevel10OrHigher(holder.getFile());
        if (isInfoLevel && !isOnTheFly) return;
        ProblemHighlightType highlightType = isInfoLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.WARNING;
        ReplaceWithNioCallFix fix = new ReplaceWithNioCallFix(streamType.myReplacement, isOnTheFly);
        holder.registerProblem(newExpression, JavaBundle.message(streamType.myErrorText), highlightType, fix);
      }
    };
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

    PsiExpression getExpression();

    private static @Nullable ArgumentModel create(@NotNull PsiExpression argument) {
      PsiType argType = argument.getType();
      if (argType == null) return null;
      if (argType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return new StringExpr(argument);
      }
      if (argType.equalsToText(CommonClassNames.JAVA_IO_FILE)) {
        return new FileExpr(argument);
      }
      return null;
    }

    class FileExpr implements ArgumentModel {

      private final PsiExpression myFileExpr;

      private FileExpr(@NotNull PsiExpression fileExpr) {
        myFileExpr = fileExpr;
      }

      @Override
      public String createReplacement() {
        boolean needsParenthesis = PsiPrecedenceUtil.getPrecedence(myFileExpr) > PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE;
        if (needsParenthesis) return "(" + myFileExpr.getText() + ").toPath()";
        return myFileExpr.getText() + ".toPath()";
      }

      @Override
      public PsiExpression getExpression() {
        return myFileExpr;
      }
    }

    class StringExpr implements ArgumentModel {
      private final PsiExpression myStringExpr;

      private StringExpr(@NotNull PsiExpression stringExpr) {
        myStringExpr = stringExpr;
      }

      @Override
      public String createReplacement() {
        return PsiUtil.isLanguageLevel11OrHigher(myStringExpr)
               ? "java.nio.file.Path.of(" + myStringExpr.getText() + ")"
               : "java.nio.file.Paths.get(" + myStringExpr.getText() + ")";
      }

      @Override
      public PsiExpression getExpression() {
        return myStringExpr;
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
      boolean isEffectivelyFinal = isEffectivelyFinal(newExpression, constructorModel.myArgument.getExpression());
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
      List<PsiVariable> pathVars = Arrays.stream(occurrences).map(o -> findVariableAssignedTo(o))
        .filter(var -> var != null && HighlightControlFlowUtil.isEffectivelyFinal(var, toPathConversion, null))
        .collect(Collectors.toList());
      if (!pathVars.isEmpty()) {
        PsiCodeBlock body = containingMethod.getBody();
        if (body == null) return;
        ControlFlow flow = createControlFlow(body);
        if (flow == null) return;
        int conversionOffset = flow.getStartOffset(toPathConversion);
        if (conversionOffset == -1) return;
        Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, conversionOffset, true);
        PsiVariable pathVar = ContainerUtil.find(pathVars, var -> writtenVariables.contains(var));
        if (pathVar != null) {
          String varName = pathVar.getName();
          if (varName == null) return;
          PsiReplacementUtil.replaceExpressionAndShorten(toPathConversion, varName, new CommentTracker());
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

    private static @Nullable PsiVariable findVariableAssignedTo(@NotNull PsiExpression occurrence) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(occurrence);
      PsiElement context = PsiTreeUtil.getParentOfType(parent, PsiVariable.class, PsiAssignmentExpression.class);
      PsiVariable variable = ObjectUtils.tryCast(context, PsiVariable.class);
      if (variable != null) {
        if (variable.getInitializer() != parent) return null;
        return variable;
      }
      if (context == null) return null;
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)context;
      if (assignment.getRExpression() != parent) return null;
      PsiReferenceExpression ref = ObjectUtils.tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (ref == null) return null;
      PsiVariable target = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
      if (!PsiUtil.isJvmLocalVariable(target)) return null;
      return target;
    }

    private static boolean isEffectivelyFinal(PsiElement context, PsiExpression expression) {
      return ExpressionUtils.nonStructuralChildren(expression).allMatch(c -> isEffectivelyFinal(context, expression, c));
    }

    private static boolean isEffectivelyFinal(PsiElement context, PsiExpression parent, PsiExpression child) {
      if (child != parent) return isEffectivelyFinal(context, child);
      if (child instanceof PsiLiteralExpression) return true;
      if (!(child instanceof PsiReferenceExpression)) return false;
      PsiVariable target = ObjectUtils.tryCast(((PsiReferenceExpression)child).resolve(), PsiVariable.class);
      if (!PsiUtil.isJvmLocalVariable(target)) return false;
      return HighlightControlFlowUtil.isEffectivelyFinal(target, context, null);
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
