// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class WrapperTypeMayBePrimitiveInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher TO_STRING = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "toString");
  private static final CallMatcher HASH_CODE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "hashCode");
  private static final CallMatcher VALUE_OF = getValueOfMatcher();

  private static final Map<String, String> ourReplacementMap = new HashMap<>();

  static {
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_INTEGER, "parseInt");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_LONG, "parseLong");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "parseBoolean");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_SHORT, "parseShort");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_BYTE, "parseByte");
  }

  static CallMatcher getValueOfMatcher() {
    CallMatcher[] matchers = JvmPrimitiveTypeKind.getBoxedFqns()
                                                 .stream()
                                             .filter(fqn -> !fqn.equals(CommonClassNames.JAVA_LANG_CHARACTER))
                                             .map(fqn -> CallMatcher.staticCall(fqn, "valueOf")
                                                                  .parameterTypes(CommonClassNames.JAVA_LANG_STRING))
                                             .toArray(size -> new CallMatcher[size]);
    return CallMatcher.anyOf(matchers);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        WrapperTypeMayBePrimitiveDetectingVisitor visitor = new WrapperTypeMayBePrimitiveDetectingVisitor();
        body.accept(visitor);
        for (PsiLocalVariable variable : visitor.getVariablesToUnbox()) {
          holder.registerProblem(variable.getTypeElement(), InspectionsBundle.message("inspection.wrapper.type.may.be.primitive.name"),
                                 new ConvertWrapperTypeToPrimitive());
        }
      }
    };
  }

  private static class BoxingInfo {
    private final @NotNull PsiLocalVariable myVariable;
    boolean myHasReferences = false;
    private int myBoxedUnnecessaryOperationCount = 0;
    private int myUnboxedUnnecessaryOperationCount = 0;

    private BoxingInfo(@NotNull PsiLocalVariable variable) {myVariable = variable;}

    /**
     * Check, whether expression passed as argument is suitable to be right part of assignment or initializer when variable will be primitive
     * Also collect statistics if boxing needed or unboxing needed
     *
     * @return false if boxing is required anyway
     */
    boolean checkExpression(@NotNull PsiExpression expression) {
      if (expression.getType() instanceof PsiPrimitiveType && !PsiType.NULL.equals(expression.getType())) {
        myBoxedUnnecessaryOperationCount++;
      }
      else if (!isValueOfCall(expression)) {
        if (NullnessUtil.getExpressionNullness(expression) != Nullness.NOT_NULL) { // not safe using with primitive
          return false;
        }
        myUnboxedUnnecessaryOperationCount++;
      }
      return true;
    }

    boolean primitiveReplacementReducesUnnecessaryOperationCount() {
      return myUnboxedUnnecessaryOperationCount < myBoxedUnnecessaryOperationCount;
    }
  }

  private static boolean isValueOfCall(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression && VALUE_OF.test((PsiMethodCallExpression)expression);
  }

  private static class WrapperTypeMayBePrimitiveDetectingVisitor extends JavaRecursiveElementWalkingVisitor {
    private static final int IN_LOOP_ASSIGNMENT_OPERATION_MULTIPLIER = 10;

    // name to list of boxes
    private final Map<String, List<BoxingInfo>> myBoxingMap = new HashMap<>();

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      if (!TypeConversionUtil.isPrimitiveWrapper(variable.getType())) return;
      PsiExpression initializer = variable.getInitializer();
      BoxingInfo boxingInfo = new BoxingInfo(variable);
      if (initializer != null && !boxingInfo.checkExpression(initializer)) return;
      String name = variable.getName();
      if (name == null) return;
      ArrayList<BoxingInfo> infos = new ArrayList<>();
      infos.add(boxingInfo);
      myBoxingMap.put(name, infos);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      String name = expression.getReferenceName();
      if (name == null) return;
      List<BoxingInfo> infos = myBoxingMap.get(name);
      if (infos == null) return;
      Iterator<BoxingInfo> iterator = infos.iterator();
      while (iterator.hasNext()) {
        BoxingInfo boxingInfo = iterator.next();
        if (!ExpressionUtils.isReferenceTo(expression, boxingInfo.myVariable)) continue;
        boxingInfo.myHasReferences = true;
        if (!referenceUseAllowUnboxing(expression, boxingInfo)) {
          iterator.remove();
        }
        break;
      }
      if (infos.isEmpty()) {
        myBoxingMap.remove(name);
      }
    }

    public List<PsiLocalVariable> getVariablesToUnbox() {
      List<PsiLocalVariable> variables = new ArrayList<>();
      for (List<BoxingInfo> infos : myBoxingMap.values()) {
        for (BoxingInfo boxingInfo : infos) {
          if (boxingInfo.myHasReferences && boxingInfo.primitiveReplacementReducesUnnecessaryOperationCount()) {
            variables.add(boxingInfo.myVariable);
          }
        }
      }
      return variables;
    }

    private static boolean referenceUseAllowUnboxing(@NotNull PsiReferenceExpression expression,
                                                     @NotNull BoxingInfo boxingInfo) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression).getParent();
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
      if (call != null) {
        return TO_STRING.test(call) || HASH_CODE.test(call);
      }
      if (parent instanceof PsiExpressionList) {
        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiCallExpression)) return true;
        PsiExpression[] arguments = ((PsiExpressionList)parent).getExpressions();
        int argumentsIndex = ArrayUtil.indexOf(arguments, expression);
        if (argumentsIndex == -1) return true;
        PsiCallExpression callExpression = (PsiCallExpression)grandParent;
        PsiMethod method = callExpression.resolveMethod();
        if (method == null) return true;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        int parameterIndex = parameters.length < argumentsIndex + 1 ? parameters.length - 1 : argumentsIndex;
        if (parameterIndex < 0) return false;
        PsiParameter parameter = parameters[parameterIndex];
        PsiType type = parameter.getType();
        if (type instanceof PsiPrimitiveType) {
          boxingInfo.myBoxedUnnecessaryOperationCount++;
        }
        else {
          boxingInfo.myUnboxedUnnecessaryOperationCount++;
        }
      }
      else if (parent instanceof PsiAssignmentExpression) {
        PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
        if (rExpression == null) return true;
        if (!boxingInfo.checkExpression(rExpression)) return false;
      }
      else if (parent instanceof PsiSynchronizedStatement) {
        return false;
      }
      else if (parent instanceof PsiBinaryExpression) {
        return binaryExpressionUseAllowUnboxing((PsiBinaryExpression)parent, boxingInfo);
      }
      else if (parent instanceof PsiReturnStatement) {
        PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, false, PsiLambdaExpression.class);
        if (method != null) {
          PsiType returnType = method.getReturnType();
          if (returnType != null) {
            if (returnType instanceof PsiPrimitiveType) {
              boxingInfo.myBoxedUnnecessaryOperationCount++;
            }
            else {
              boxingInfo.myUnboxedUnnecessaryOperationCount++;
            }
          }
        }
      }
      return true;
    }

    private static boolean binaryExpressionUseAllowUnboxing(@NotNull PsiBinaryExpression binaryExpression,
                                                            @NotNull BoxingInfo boxingInfo) {
      IElementType operationTokenType = binaryExpression.getOperationTokenType();
      PsiExpression other = ExpressionUtils.getOtherOperand(binaryExpression, boxingInfo.myVariable);
      PsiType type = other.getType();
      if (operationTokenType == JavaTokenType.EQEQ || operationTokenType == JavaTokenType.NE) {
        if (!(type instanceof PsiPrimitiveType) || PsiType.NULL.equals(type)) return false;
        boxingInfo.myBoxedUnnecessaryOperationCount++;
      }

      int boxedUnnecessaryOpImpact = 0;
      int unboxedUnnecessaryOpImpact = 0;
      if (type instanceof PsiPrimitiveType) {
        if (PsiType.NULL.equals(type)) return false;
        if (TypeConversionUtil.convertEQtoOperation(binaryExpression.getOperationTokenType()) != null) {
          boxedUnnecessaryOpImpact += 2;
        }
      }
      else {
        if (NullnessUtil.getExpressionNullness(other) != Nullness.NOT_NULL) return false;
        boxedUnnecessaryOpImpact += 3;
        unboxedUnnecessaryOpImpact += 3;
      }
      PsiLoopStatement binopLoop =
        PsiTreeUtil.getParentOfType(binaryExpression, PsiLoopStatement.class, false, PsiClass.class, PsiLambdaExpression.class);
      PsiLoopStatement variableLoop =
        PsiTreeUtil.getParentOfType(boxingInfo.myVariable, PsiLoopStatement.class, false, PsiClass.class, PsiLambdaExpression.class);
      if (binopLoop != null && binopLoop == variableLoop) {
        boxedUnnecessaryOpImpact *= IN_LOOP_ASSIGNMENT_OPERATION_MULTIPLIER;
        unboxedUnnecessaryOpImpact *= IN_LOOP_ASSIGNMENT_OPERATION_MULTIPLIER;
      }
      boxingInfo.myBoxedUnnecessaryOperationCount += boxedUnnecessaryOpImpact;
      boxingInfo.myUnboxedUnnecessaryOperationCount += unboxedUnnecessaryOpImpact;

      return true;
    }
  }

  private static class ConvertWrapperTypeToPrimitive implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.wrapper.type.may.be.primitive.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiTypeElement typeElement = tryCast(element, PsiTypeElement.class);
      if (typeElement == null) return;
      PsiLocalVariable variable = tryCast(typeElement.getParent(), PsiLocalVariable.class);
      if (variable == null) return;
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        tryReplaceStaticCall(initializer);
      }
      PsiType type = variable.getType();
      String boxedType = type.getCanonicalText();
      String unboxedType = PsiTypesUtil.unboxIfPossible(boxedType);
      if (unboxedType.equals(boxedType)) return;
      PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, null);
      if (codeBlock == null) return;
      codeBlock.accept(new UnboxingVisitor(variable));
      new CommentTracker().replaceAndRestoreComments(typeElement, unboxedType);
    }

    private static class UnboxingVisitor extends JavaRecursiveElementVisitor {
      private final PsiLocalVariable myVariable;

      public UnboxingVisitor(PsiLocalVariable variable) {myVariable = variable;}

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (!ExpressionUtils.isReferenceTo(expression, myVariable)) return;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression).getParent();
        PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
        if (call != null) {
          replaceInstanceCall(call);
        }
        else if (parent instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
          if (!ExpressionUtils.isReferenceTo(assignment.getLExpression(), myVariable)) return;
          tryReplaceStaticCall(assignment.getRExpression());
        }
      }
    }

    private static void tryReplaceStaticCall(PsiExpression expression) {
      PsiMethodCallExpression callExpression = tryCast(expression, PsiMethodCallExpression.class);
      if (!VALUE_OF.test(callExpression)) return;
      PsiMethod method = callExpression.resolveMethod();
      if (method == null) return;
      PsiClass containingClass = method.getContainingClass();
      PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
      if (arguments.length != 1) return;
      PsiExpression argument = arguments[0];
      if (containingClass == null) return;
      String containingClassName = containingClass.getQualifiedName();
      String replacementMethodCall = ourReplacementMap.get(containingClassName);
      if (replacementMethodCall == null) return;
      CommentTracker tracker = new CommentTracker();
      String argumentText = tracker.text(argument);
      String replacementText = containingClassName + "." + replacementMethodCall + "(" + argumentText + ")";
      tracker.replaceAndRestoreComments(callExpression, replacementText);
    }

    private static void replaceInstanceCall(PsiMethodCallExpression call) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) return;
      String qualifierTypeText = qualifierType.getCanonicalText();
      CommentTracker tracker = new CommentTracker();
      String qualifierText = tracker.text(qualifier);
      String methodNameText;
      if (HASH_CODE.test(call)) {
        methodNameText = "hashCode";
      }
      else if (TO_STRING.test(call)) {
        methodNameText = "toString";
      }
      else {
        return;
      }
      String callReplacementText = qualifierTypeText + "." + methodNameText + "(" + qualifierText + ")";
      tracker.replaceAndRestoreComments(call, callReplacementText);
    }
  }
}
