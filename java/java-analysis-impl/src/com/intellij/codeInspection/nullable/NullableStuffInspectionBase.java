// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

public class NullableStuffInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  // deprecated fields remain to minimize changes to users inspection profiles (which are often located in version control).
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @SuppressWarnings("WeakerAccess") public boolean IGNORE_EXTERNAL_SUPER_NOTNULL;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED;
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true; // remains for test
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NULLS_PASSED_TO_NON_ANNOTATED_METHOD = true;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;

  private static final Logger LOG = Logger.getInstance(NullableStuffInspectionBase.class);

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    for (Element child : new ArrayList<>(node.getChildren())) {
      String name = child.getAttributeValue("name");
      String value = child.getAttributeValue("value");
      if ("IGNORE_EXTERNAL_SUPER_NOTNULL".equals(name) && "false".equals(value) ||
          "REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED".equals(name) && "false".equals(value) ||
          "REQUIRE_NOTNULL_FIELDS_INITIALIZED".equals(name) && "true".equals(value) ||
          "REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER".equals(name) && "true".equals(value)) {
        node.removeContent(child);
      }
    }
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    final PsiFile file = holder.getFile();
    if (!PsiUtil.isLanguageLevel5OrHigher(file) || nullabilityAnnotationsNotAvailable(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        checkNullableStuffForMethod(method, holder, isOnTheFly);
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        checkMethodReference(expression, holder);

        JavaResolveResult result = expression.advancedResolve(false);
        PsiElement target = result.getElement();
        if (target instanceof PsiMethod) {
          checkCollectionNullityOnAssignment(expression,
                                             LambdaUtil.getFunctionalInterfaceReturnType(expression),
                                             result.getSubstitutor().substitute(((PsiMethod)target).getReturnType()));
        }
      }

      @Override
      public void visitField(PsiField field) {
        final PsiType type = field.getType();
        final Annotated annotated = check(field, holder, type);
        if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
          return;
        }
        Project project = holder.getProject();
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        if (annotated.isDeclaredNotNull ^ annotated.isDeclaredNullable) {
          final String anno = annotated.isDeclaredNotNull ? manager.getDefaultNotNull() : manager.getDefaultNullable();
          final List<String> annoToRemove = annotated.isDeclaredNotNull ? manager.getNullables() : manager.getNotNulls();

          if (!checkNonStandardAnnotations(field, annotated, manager, anno, holder)) return;

          checkAccessors(field, annotated, project, manager, anno, annoToRemove, holder);

          checkConstructorParameters(field, annotated, manager, anno, annoToRemove, holder);
        }
      }

      @Override
      public void visitParameter(PsiParameter parameter) {
        check(parameter, holder, parameter.getType());
      }

      @Override
      public void visitTypeElement(PsiTypeElement type) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(type.getProject());
        List<PsiAnnotation> annotations = getExclusiveAnnotations(type);

        checkType(null, holder, type.getType(),
                  ContainerUtil.find(annotations, a -> manager.getNotNulls().contains(a.getQualifiedName())),
                  ContainerUtil.find(annotations, a -> manager.getNullables().contains(a.getQualifiedName())));
      }

      private List<PsiAnnotation> getExclusiveAnnotations(PsiTypeElement type) {
        List<PsiAnnotation> annotations = ContainerUtil.newArrayList(type.getAnnotations());
        PsiTypeElement topMost = Objects.requireNonNull(SyntaxTraverser.psiApi().parents(type).filter(PsiTypeElement.class).last());
        PsiElement parent = topMost.getParent();
        if (parent instanceof PsiModifierListOwner && type.getType().equals(topMost.getType().getDeepComponentType())) {
          PsiModifierList modifierList = ((PsiModifierListOwner)parent).getModifierList();
          if (modifierList != null) {
            PsiAnnotation.TargetType[] targets = ArrayUtil.remove(AnnotationTargetUtil.getTargetsForLocation(modifierList), PsiAnnotation.TargetType.TYPE_USE);
            annotations.addAll(ContainerUtil.filter(modifierList.getAnnotations(),
                                                    a -> AnnotationTargetUtil.isTypeAnnotation(a) && AnnotationTargetUtil.findAnnotationTarget(a, targets) == null));
          }
        }
        return annotations;
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        if (!AnnotationUtil.NOT_NULL.equals(annotation.getQualifiedName())) return;

        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("exception");
        if (value instanceof PsiClassObjectAccessExpression) {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(((PsiClassObjectAccessExpression)value).getOperand().getType());
          if (psiClass != null && !hasStringConstructor(psiClass)) {
            holder.registerProblem(value,
                                   JavaAnalysisBundle.message(
                                     "custom.exception.class.should.have.a.constructor"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

          }
        }
      }

      private boolean hasStringConstructor(PsiClass aClass) {
        for (PsiMethod method : aClass.getConstructors()) {
          PsiParameterList list = method.getParameterList();
          if (list.getParametersCount() == 1 &&
              Objects.requireNonNull(list.getParameter(0)).getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        checkNullableNotNullInstantiationConflict(reference);

        PsiElement list = reference.getParent();
        PsiElement psiClass = list instanceof PsiReferenceList ? list.getParent() : null;
        PsiElement intf = reference.resolve();
        if (psiClass instanceof PsiClass && list == ((PsiClass)psiClass).getImplementsList() &&
            intf instanceof PsiClass && ((PsiClass)intf).isInterface()) {
          String error = checkIndirectInheritance(psiClass, (PsiClass)intf);
          if (error != null) {
            holder.registerProblem(reference, error);
          }
        }
      }

      private void checkNullableNotNullInstantiationConflict(PsiJavaCodeReferenceElement reference) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiClass) {
          PsiTypeParameter[] typeParameters = ((PsiClass)element).getTypeParameters();
          PsiTypeElement[] typeArguments = getReferenceTypeArguments(reference);
          if (typeParameters.length > 0 && typeParameters.length == typeArguments.length && !(typeArguments[0].getType() instanceof PsiDiamondType)) {
            for (int i = 0; i < typeParameters.length; i++) {
              if (DfaPsiUtil.getTypeNullability(JavaPsiFacade.getElementFactory(element.getProject()).createType(typeParameters[i])) ==
                  Nullability.NOT_NULL && DfaPsiUtil.getTypeNullability(typeArguments[i].getType()) != Nullability.NOT_NULL) {
                holder.registerProblem(typeArguments[i],
                                       JavaAnalysisBundle.message("non.null.type.argument.is.expected"),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
              }
            }
          }
        }
      }

      private PsiTypeElement[] getReferenceTypeArguments(PsiJavaCodeReferenceElement reference) {
        PsiReferenceParameterList typeArgList = reference.getParameterList();
        return typeArgList == null ? PsiTypeElement.EMPTY_ARRAY : typeArgList.getTypeParameterElements();
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        checkCollectionNullityOnAssignment(expression.getOperationSign(), expression.getLExpression().getType(), expression.getRExpression());
      }

      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier != null) {
          checkCollectionNullityOnAssignment(identifier, variable.getType(), variable.getInitializer());
        }
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (returnValue == null) return;

        checkCollectionNullityOnAssignment(statement.getReturnValue(), PsiTypesUtil.getMethodReturnType(statement), returnValue);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression) {
          checkCollectionNullityOnAssignment(body, LambdaUtil.getFunctionalInterfaceReturnType(lambda), (PsiExpression)body);
        }
      }

      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        PsiExpressionList argList = callExpression.getArgumentList();
        JavaResolveResult result = callExpression.resolveMethodGenerics();
        PsiMethod method = (PsiMethod)result.getElement();
        if (method == null || argList == null) return;

        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiExpression[] arguments = argList.getExpressions();
        for (int i = 0; i < arguments.length; i++) {
          PsiExpression argument = arguments[i];
          if (i < parameters.length &&
              (i < parameters.length - 1 || !MethodCallInstruction.isVarArgCall(method, substitutor, arguments, parameters))) {
            checkCollectionNullityOnAssignment(argument, substitutor.substitute(parameters[i].getType()), argument);
          }
        }
      }

      private void checkCollectionNullityOnAssignment(@NotNull PsiElement errorElement,
                                                      @Nullable PsiType expectedType,
                                                      @Nullable PsiExpression assignedExpression) {
        if (assignedExpression == null) return;

        checkCollectionNullityOnAssignment(errorElement, expectedType, assignedExpression.getType());
      }

      private void checkCollectionNullityOnAssignment(@NotNull PsiElement errorElement,
                                                      @Nullable PsiType expectedType,
                                                      @Nullable PsiType assignedType) {
        if (isNullableNotNullCollectionConflict(expectedType, assignedType, file, new HashSet<>())) {
          holder.registerProblem(errorElement,
                                 JavaAnalysisBundle
                                   .message("assigning.a.collection.of.nullable.elements"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

        }
      }

    };
  }

  private static boolean isNullableNotNullCollectionConflict(@Nullable PsiType expectedType,
                                                             @Nullable PsiType assignedType,
                                                             @NotNull PsiFile place,
                                                             @NotNull Set<? super Couple<PsiType>> visited) {
    if (!visited.add(Couple.of(expectedType, assignedType))) return false;

    GlobalSearchScope scope = place.getResolveScope();
    if (isNullityConflict(JavaGenericsUtil.getCollectionItemType(expectedType, scope),
                          JavaGenericsUtil.getCollectionItemType(assignedType, scope))) {
      return true;
    }

    for (int i = 0; i <= 1; i++) {
      PsiType expectedArg = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
      PsiType assignedArg = PsiUtil.substituteTypeParameter(assignedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
      if (isNullityConflict(expectedArg, assignedArg) ||
          expectedArg != null && assignedArg != null && isNullableNotNullCollectionConflict(expectedArg, assignedArg, place, visited)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isNullityConflict(PsiType expected, PsiType assigned) {
    return DfaPsiUtil.getTypeNullability(expected) == Nullability.NOT_NULL && DfaPsiUtil.getTypeNullability(assigned) == Nullability.NULLABLE;
  }

  @Nullable
  private String checkIndirectInheritance(PsiElement psiClass, PsiClass intf) {
    for (PsiMethod intfMethod : intf.getAllMethods()) {
      PsiClass intfMethodClass = intfMethod.getContainingClass();
      PsiMethod overridingMethod = intfMethodClass == null ? null :
                                   JavaOverridingMethodsSearcher.findOverridingMethod((PsiClass)psiClass, intfMethod, intfMethodClass);
      PsiClass overridingMethodClass = overridingMethod == null ? null : overridingMethod.getContainingClass();
      if (overridingMethodClass != null && overridingMethodClass != psiClass) {
        String error = checkIndirectInheritance(intfMethod, intfMethodClass, overridingMethod, overridingMethodClass);
        if (error != null) return error;
      }
    }
    return null;
  }

  @Nullable
  private String checkIndirectInheritance(PsiMethod intfMethod,
                                          PsiClass intfMethodClass,
                                          PsiMethod overridingMethod,
                                          PsiClass overridingMethodClass) {
    if (isNullableOverridingNotNull(Annotated.from(overridingMethod), intfMethod)) {
      return "Nullable method '" + overridingMethod.getName() +
             "' from '" + overridingMethodClass.getName() +
             "' implements non-null method from '" + intfMethodClass.getName() + "'";
    }
    if (isNonAnnotatedOverridingNotNull(overridingMethod, intfMethod)) {
      return "Non-annotated method '" + overridingMethod.getName() +
             "' from '" + overridingMethodClass.getName() +
             "' implements non-null method from '" + intfMethodClass.getName() + "'";
    }

    PsiParameter[] overridingParameters = overridingMethod.getParameterList().getParameters();
    PsiParameter[] superParameters = intfMethod.getParameterList().getParameters();
    if (overridingParameters.length == superParameters.length) {
      NullableNotNullManager manager = getNullityManager(intfMethod);
      for (int i = 0; i < overridingParameters.length; i++) {
        PsiParameter parameter = overridingParameters[i];
        List<PsiParameter> supers = Collections.singletonList(superParameters[i]);
        if (findNullableSuperForNotNullParameter(parameter, supers) != null) {
          return "Non-null parameter '" + parameter.getName() +
                 "' in method '" + overridingMethod.getName() +
                 "' from '" + overridingMethodClass.getName() +
                 "' should not override nullable parameter from '" + intfMethodClass.getName() + "'";
        }
        if (findNotNullSuperForNonAnnotatedParameter(manager, parameter, supers) != null) {
          return "Non-annotated parameter '" + parameter.getName() +
                 "' in method '" + overridingMethod.getName() +
                 "' from '" + overridingMethodClass.getName() +
                 "' should not override non-null parameter from '" + intfMethodClass.getName() + "'";
        }
        if (isNotNullParameterOverridingNonAnnotated(manager, parameter, supers)) {
          return "Non-null parameter '" + parameter.getName() +
                 "' in method '" + overridingMethod.getName() +
                 "' from '" + overridingMethodClass.getName() +
                 "' should not override non-annotated parameter from '" + intfMethodClass.getName() + "'";
        }
      }
    }

    return null;
  }

  private void checkMethodReference(PsiMethodReferenceExpression expression, @NotNull ProblemsHolder holder) {
    PsiMethod superMethod = LambdaUtil.getFunctionalInterfaceMethod(expression);
    PsiMethod targetMethod = ObjectUtils.tryCast(expression.resolve(), PsiMethod.class);
    if (superMethod == null || targetMethod == null) return;

    PsiElement refName = expression.getReferenceNameElement();
    assert refName != null;
    if (isNullableOverridingNotNull(check(targetMethod, holder, expression.getType()), superMethod)) {
      holder.registerProblem(refName,
                             JavaAnalysisBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull",
                                                       getPresentableAnnoName(targetMethod), getPresentableAnnoName(superMethod)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    else if (isNonAnnotatedOverridingNotNull(targetMethod, superMethod)) {
      holder.registerProblem(refName,
                             JavaAnalysisBundle.message("not.annotated.method.is.used.as.an.override.for.a.method.annotated.with.0",
                                                       getPresentableAnnoName(superMethod)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             createFixForNonAnnotatedOverridesNotNull(targetMethod, superMethod));
    }
  }

  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return null;
  }

  private static boolean nullabilityAnnotationsNotAvailable(final PsiFile file) {
    final Project project = file.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    return ContainerUtil.find(NullableNotNullManager.getInstance(project).getNullables(), s -> facade.findClass(s, scope) != null) == null;
  }

  private static boolean checkNonStandardAnnotations(PsiField field,
                                                     Annotated annotated,
                                                     NullableNotNullManager manager, String anno, @NotNull ProblemsHolder holder) {
    if (!AnnotationUtil.isAnnotatingApplicable(field, anno)) {
      String message = "Not '";
      PsiAnnotation annotation = Objects.requireNonNull(annotated.isDeclaredNullable ? annotated.nullable : annotated.notNull);
      message += annotation.getQualifiedName();
      message += "' but '" + anno + "' would be used for code generation.";
      final PsiJavaCodeReferenceElement annotationNameReferenceElement = annotation.getNameReferenceElement();
      holder.registerProblem(annotationNameReferenceElement != null && annotationNameReferenceElement.isPhysical() ? annotationNameReferenceElement : field.getNameIdentifier(),
                             message,
                             ProblemHighlightType.WEAK_WARNING,
                             new ChangeNullableDefaultsFix(annotated.notNull, annotated.nullable, manager));
      return false;
    }
    return true;
  }

  private void checkAccessors(PsiField field,
                              Annotated annotated,
                              Project project,
                              NullableNotNullManager manager, final String anno, final List<String> annoToRemove, @NotNull ProblemsHolder holder) {
    String propName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiMethod getter = PropertyUtilBase.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
    final PsiIdentifier nameIdentifier = getter == null ? null : getter.getNameIdentifier();
    if (nameIdentifier != null && nameIdentifier.isPhysical()) {
      if (PropertyUtil.getFieldOfGetter(getter) == field) {
        AnnotateMethodFix getterAnnoFix = new AnnotateMethodFix(anno, ArrayUtilRt.toStringArray(annoToRemove));
        if (REPORT_NOT_ANNOTATED_GETTER) {
          if (!manager.hasNullability(getter) && !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
            holder.registerProblem(nameIdentifier, JavaAnalysisBundle
                                     .message("inspection.nullable.problems.annotated.field.getter.not.annotated", getPresentableAnnoName(field)),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
          }
        }
        if (annotated.isDeclaredNotNull && isNullableNotInferred(getter, false) ||
            annotated.isDeclaredNullable && isNotNullNotInferred(getter, false, false)) {
          holder.registerProblem(nameIdentifier, JavaAnalysisBundle.message(
                                   "inspection.nullable.problems.annotated.field.getter.conflict", getPresentableAnnoName(field), getPresentableAnnoName(getter)),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
        }
      }
    }

    final PsiClass containingClass = field.getContainingClass();
    final PsiMethod setter = PropertyUtilBase.findPropertySetter(containingClass, propName, isStatic, false);
    if (setter != null && setter.isPhysical() && PropertyUtil.getFieldOfSetter(setter) == field) {
      final PsiParameter[] parameters = setter.getParameterList().getParameters();
      assert parameters.length == 1 : setter.getText();
      final PsiParameter parameter = parameters[0];
      LOG.assertTrue(parameter != null, setter.getText());
      AddAnnotationPsiFix addAnnoFix = createAddAnnotationFix(anno, annoToRemove, parameter);
      if (REPORT_NOT_ANNOTATED_GETTER && !manager.hasNullability(parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
        final PsiIdentifier parameterName = parameter.getNameIdentifier();
        assertValidElement(setter, parameter, parameterName);
        holder.registerProblem(parameterName,
                               JavaAnalysisBundle.message("inspection.nullable.problems.annotated.field.setter.parameter.not.annotated",
                                                         getPresentableAnnoName(field)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               addAnnoFix);
      }
      if (PropertyUtil.isSimpleSetter(setter)) {
        if (annotated.isDeclaredNotNull && isNullableNotInferred(parameter, false)) {
          final PsiIdentifier parameterName = parameter.getNameIdentifier();
          assertValidElement(setter, parameter, parameterName);
          holder.registerProblem(parameterName, JavaAnalysisBundle.message(
                                   "inspection.nullable.problems.annotated.field.setter.parameter.conflict",
                                   getPresentableAnnoName(field), getPresentableAnnoName(parameter)),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 addAnnoFix);
        }
      }
    }
  }

  @NotNull
  private static AddAnnotationPsiFix createAddAnnotationFix(String anno, List<String> annoToRemove, PsiParameter parameter) {
    return new AddAnnotationPsiFix(anno, parameter, PsiNameValuePair.EMPTY_ARRAY, ArrayUtilRt.toStringArray(annoToRemove));
  }

  @Contract("_,_,null -> fail")
  private static void assertValidElement(PsiMethod setter, PsiParameter parameter, PsiIdentifier nameIdentifier1) {
    LOG.assertTrue(nameIdentifier1 != null && nameIdentifier1.isPhysical(), setter.getText());
    LOG.assertTrue(parameter.isPhysical(), setter.getText());
  }

  private void checkConstructorParameters(PsiField field,
                                          Annotated annotated,
                                          NullableNotNullManager manager,
                                          String anno, List<String> annoToRemove, @NotNull ProblemsHolder holder) {
    List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
    if (initializers.isEmpty()) return;

    List<PsiParameter> notNullParams = new ArrayList<>();

    boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);

    for (PsiExpression rhs : initializers) {
      if (rhs instanceof PsiReferenceExpression) {
        PsiElement target = ((PsiReferenceExpression)rhs).resolve();
        if (isConstructorParameter(target) && target.isPhysical()) {
          PsiParameter parameter = (PsiParameter)target;
          if (REPORT_NOT_ANNOTATED_GETTER && !manager.hasNullability(parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
            final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            if (nameIdentifier != null && nameIdentifier.isPhysical()) {
              holder.registerProblem(
                nameIdentifier,
                JavaAnalysisBundle.message("inspection.nullable.problems.annotated.field.constructor.parameter.not.annotated", getPresentableAnnoName(field)),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createAddAnnotationFix(anno, annoToRemove, parameter));
              continue;
            }
          }

          if (isFinal && annotated.isDeclaredNullable && isNotNullNotInferred(parameter, false, false)) {
            notNullParams.add(parameter);
          }

        }
      }
    }

    if (notNullParams.size() != initializers.size()) {
      // it's not the case that the field is final and @Nullable and always initialized via @NotNull parameters
      // so there might be other initializers that could justify it being nullable
      // so don't highlight field and constructor parameter annotation inconsistency
      return;
    }

    PsiIdentifier nameIdentifier = field.getNameIdentifier();
    if (nameIdentifier.isPhysical()) {
      holder.registerProblem(nameIdentifier,
                             JavaAnalysisBundle.message("0.field.is.always.initialized.not.null", getPresentableAnnoName(field)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, AddAnnotationPsiFix.createAddNotNullFix(field));
    }
  }

  private static boolean isConstructorParameter(@Nullable PsiElement parameter) {
    return parameter instanceof PsiParameter && psiElement(PsiParameterList.class).withParent(psiMethod().constructor(true)).accepts(parameter.getParent());
  }

  @NotNull
  private static String getPresentableAnnoName(@NotNull PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    String name = info == null ? null : info.getAnnotation().getQualifiedName();
    if (name == null) {
      return "???";
    }
    return StringUtil.getShortName(name);
  }

  public static String getPresentableAnnoName(@NotNull PsiAnnotation annotation) {
    return StringUtil.getShortName(StringUtil.notNullize(annotation.getQualifiedName(), "???"));
  }

  private static final class Annotated {
    private final boolean isDeclaredNotNull;
    private final boolean isDeclaredNullable;
    @Nullable private final PsiAnnotation notNull;
    @Nullable private final PsiAnnotation nullable;

    private Annotated(@Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
      this.isDeclaredNotNull = notNull != null;
      this.isDeclaredNullable = nullable != null;
      this.notNull = notNull;
      this.nullable = nullable;
    }

    @NotNull static Annotated from(@NotNull PsiModifierListOwner owner) {
      NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
      return new Annotated(manager.findExplicitNullabilityAnnotation(owner, Nullability.NOT_NULL),
                           manager.findExplicitNullabilityAnnotation(owner, Nullability.NULLABLE));
    }
  }

  private static Annotated check(final PsiModifierListOwner owner, final ProblemsHolder holder, PsiType type) {
    Annotated annotated = Annotated.from(owner);
    checkType(owner, holder, type, annotated.notNull, annotated.nullable);
    return annotated;
  }

  private static void checkType(@Nullable PsiModifierListOwner listOwner,
                                ProblemsHolder holder,
                                PsiType type,
                                @Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
    if (nullable != null && notNull != null) {
      reportNullableNotNullConflict(holder, listOwner, nullable, notNull);
    }
    if ((notNull != null || nullable != null) && type != null && TypeConversionUtil.isPrimitive(type.getCanonicalText())) {
      PsiAnnotation annotation = notNull == null ? nullable : notNull;
      if (listOwner == null || !annotation.isPhysical() || annotation.getOwner() == listOwner.getModifierList()) {
        reportPrimitiveType(holder, annotation, listOwner);
      }
    }
    if (listOwner instanceof PsiParameter) {
      checkLoopParameterNullability(holder, notNull, nullable, DfaPsiUtil.inferParameterNullability((PsiParameter)listOwner));
    }
  }

  private static void checkLoopParameterNullability(ProblemsHolder holder, @Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable, Nullability expectedNullability) {
    if (notNull != null && expectedNullability == Nullability.NULLABLE) {
      holder.registerProblem(notNull, JavaAnalysisBundle.message("parameter.can.be.null"),
                             new RemoveAnnotationQuickFix(notNull, null));
    }
    else if (nullable != null && expectedNullability == Nullability.NOT_NULL) {
      holder.registerProblem(nullable, JavaAnalysisBundle.message("parameter.is.always.not.null"),
                             new RemoveAnnotationQuickFix(nullable, null));
    }
  }

  private static void reportPrimitiveType(ProblemsHolder holder, PsiAnnotation annotation,
                                          @Nullable PsiModifierListOwner listOwner) {
    holder.registerProblem(!annotation.isPhysical() && listOwner != null ? listOwner.getNavigationElement() : annotation,
                           JavaAnalysisBundle.message("inspection.nullable.problems.primitive.type.annotation"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(annotation, listOwner) {
        @Override
        protected boolean shouldRemoveInheritors() {
          return true;
        }
      });
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "NullableProblems";
  }

  private void checkNullableStuffForMethod(PsiMethod method, final ProblemsHolder holder, boolean isOnFly) {
    Annotated annotated = check(method, holder, method.getReturnType());

    List<PsiMethod> superMethods = ContainerUtil.map(
      method.findSuperMethodSignaturesIncludingStatic(true), MethodSignatureBackedByPsiMethod::getMethod);

    final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(holder.getProject());

    checkSupers(method, holder, annotated, superMethods);
    checkParameters(method, holder, superMethods, nullableManager, isOnFly);
    checkOverriders(method, holder, annotated, nullableManager);
  }

  private void checkSupers(PsiMethod method,
                           ProblemsHolder holder,
                           Annotated annotated,
                           List<? extends PsiMethod> superMethods) {
    for (PsiMethod superMethod : superMethods) {
      if (isNullableOverridingNotNull(annotated, superMethod)) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, getNullityManager(method).getNullables(), true);
        holder.registerProblem(annotation != null ? annotation : method.getNameIdentifier(),
                               JavaAnalysisBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull",
                                                         getPresentableAnnoName(method), getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        break;
      }

      if (isNonAnnotatedOverridingNotNull(method, superMethod)) {
        holder.registerProblem(method.getNameIdentifier(),
                               JavaAnalysisBundle
                                 .message("inspection.nullable.problems.method.overrides.NotNull", getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               createFixForNonAnnotatedOverridesNotNull(method, superMethod));
        break;
      }

      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement != null &&
          isNullableNotNullCollectionConflict(superMethod.getReturnType(), method.getReturnType(), holder.getFile(), new HashSet<>())) {
        holder.registerProblem(returnTypeElement,
                               JavaAnalysisBundle.message("nullable.stuff.error.overriding.notnull.with.nullable"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        break;
      }
    }
  }

  private static NullableNotNullManager getNullityManager(PsiMethod method) {
    return NullableNotNullManager.getInstance(method.getProject());
  }

  @Nullable
  private static LocalQuickFix createFixForNonAnnotatedOverridesNotNull(PsiMethod method,
                                                                        PsiMethod superMethod) {
    NullableNotNullManager nullableManager = getNullityManager(method);
    return AnnotationUtil.isAnnotatingApplicable(method, nullableManager.getDefaultNotNull())
                              ? AddAnnotationPsiFix.createAddNotNullFix(method)
                              : createChangeDefaultNotNullFix(nullableManager, superMethod);
  }

  private boolean isNullableOverridingNotNull(Annotated methodInfo, PsiMethod superMethod) {
    return REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && methodInfo.isDeclaredNullable && isNotNullNotInferred(superMethod, true, false);
  }

  private boolean isNonAnnotatedOverridingNotNull(PsiMethod method, PsiMethod superMethod) {
    return REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL &&
           !(method.getReturnType() instanceof PsiPrimitiveType) &&
           !method.isConstructor() &&
           !getNullityManager(method).hasNullability(method) &&
           isNotNullNotInferred(superMethod, true, IGNORE_EXTERNAL_SUPER_NOTNULL) &&
           !hasInheritableNotNull(superMethod);
  }

  private static boolean hasInheritableNotNull(PsiModifierListOwner owner) {
    return AnnotationUtil.isAnnotated(owner, "javax.annotation.constraints.NotNull", CHECK_HIERARCHY | CHECK_TYPE);
  }

  private void checkParameters(PsiMethod method,
                               ProblemsHolder holder,
                               List<? extends PsiMethod> superMethods,
                               NullableNotNullManager nullableManager,
                               boolean isOnFly) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter.getType() instanceof PsiPrimitiveType) continue;

      List<PsiParameter> superParameters = getSuperParameters(superMethods, parameters, i);

      checkSuperParameterAnnotations(holder, nullableManager, parameter, superParameters);

      checkNullLiteralArgumentOfNotNullParameterUsages(method, holder, nullableManager, isOnFly, i, parameter);
    }
  }

  @NotNull
  private static List<PsiParameter> getSuperParameters(List<? extends PsiMethod> superMethods, PsiParameter[] parameters, int i) {
    List<PsiParameter> superParameters = new ArrayList<>();
    for (PsiMethod superMethod : superMethods) {
      PsiParameter[] _superParameters = superMethod.getParameterList().getParameters();
      if (_superParameters.length == parameters.length) {
        superParameters.add(_superParameters[i]);
      }
    }
    return superParameters;
  }

  private void checkSuperParameterAnnotations(ProblemsHolder holder,
                                              NullableNotNullManager nullableManager,
                                              PsiParameter parameter,
                                              List<PsiParameter> superParameters) {
    PsiParameter nullableSuper = findNullableSuperForNotNullParameter(parameter, superParameters);
    if (nullableSuper != null) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, nullableManager.getNotNulls(), true);
      holder.registerProblem(annotation != null ? annotation : parameter.getNameIdentifier(),
                             JavaAnalysisBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
                                                        getPresentableAnnoName(parameter),
                                                        getPresentableAnnoName(nullableSuper)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    PsiParameter notNullSuper = findNotNullSuperForNonAnnotatedParameter(nullableManager, parameter, superParameters);
    if (notNullSuper != null) {
      LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, nullableManager.getDefaultNotNull())
                          ? AddAnnotationPsiFix.createAddNotNullFix(parameter)
                          : createChangeDefaultNotNullFix(nullableManager, notNullSuper);
      holder.registerProblem(parameter.getNameIdentifier(),
                             JavaAnalysisBundle.message("inspection.nullable.problems.parameter.overrides.NotNull", getPresentableAnnoName(notNullSuper)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             fix);
    }
    if (isNotNullParameterOverridingNonAnnotated(nullableManager, parameter, superParameters)) {
      NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
      assert info != null;
      PsiAnnotation notNullAnnotation = info.getAnnotation();
      boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
      final LocalQuickFix fix = physical ? new RemoveAnnotationQuickFix(notNullAnnotation, parameter) : null;
      holder.registerProblem(physical ? notNullAnnotation : parameter.getNameIdentifier(),
                             JavaAnalysisBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.not.annotated", getPresentableAnnoName(parameter)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             fix);
    }

    PsiTypeElement typeElement = parameter.getTypeElement();
    if (typeElement != null) {
      for (PsiParameter superParameter : superParameters) {
        if (isNullableNotNullCollectionConflict(parameter.getType(), superParameter.getType(), holder.getFile(), new HashSet<>())) {
          holder.registerProblem(typeElement,
                                 JavaAnalysisBundle.message("nullable.stuff.error.overriding.nullable.with.notnull"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          break;
        }
      }
    }
  }

  @Nullable
  private PsiParameter findNotNullSuperForNonAnnotatedParameter(NullableNotNullManager nullableManager,
                                                                PsiParameter parameter,
                                                                List<? extends PsiParameter> superParameters) {
    return REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !nullableManager.hasNullability(parameter)
           ? ContainerUtil.find(superParameters,
                                sp -> isNotNullNotInferred(sp, false, IGNORE_EXTERNAL_SUPER_NOTNULL) && !hasInheritableNotNull(sp))
           : null;
  }

  @Nullable
  private PsiParameter findNullableSuperForNotNullParameter(PsiParameter parameter, List<? extends PsiParameter> superParameters) {
    return REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && isNotNullNotInferred(parameter, false, false)
           ? ContainerUtil.find(superParameters, sp -> isNullableNotInferred(sp, false))
           : null;
  }

  private boolean isNotNullParameterOverridingNonAnnotated(NullableNotNullManager nullableManager,
                                                           PsiParameter parameter,
                                                           List<? extends PsiParameter> superParameters) {
    if (!REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED) return false;
    NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
    return info != null && info.getNullability() == Nullability.NOT_NULL && !info.isInferred() &&
           ContainerUtil.exists(superParameters, sp -> !nullableManager.hasNullability(sp));
  }

  private void checkNullLiteralArgumentOfNotNullParameterUsages(PsiMethod method,
                                                                ProblemsHolder holder,
                                                                NullableNotNullManager nullableManager,
                                                                boolean isOnFly,
                                                                int parameterIdx,
                                                                PsiParameter parameter) {
    if (!REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER || !isOnFly) return;

    PsiElement elementToHighlight;
    if (DfaPsiUtil.getTypeNullability(getMemberType(parameter)) == Nullability.NOT_NULL) {
      elementToHighlight = parameter.getNameIdentifier();
    }
    else {
      NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
      if (info == null || info.getNullability() != Nullability.NOT_NULL || info.isInferred()) return;
      PsiAnnotation notNullAnnotation = info.getAnnotation();
      boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
      elementToHighlight = physical ? notNullAnnotation : parameter.getNameIdentifier();
    }
    if (elementToHighlight == null || !JavaNullMethodArgumentUtil.hasNullArgument(method, parameterIdx)) return;

    holder.registerProblem(elementToHighlight,
                           JavaAnalysisBundle.message("inspection.nullable.problems.NotNull.parameter.receives.null.literal",
                                                     getPresentableAnnoName(parameter)),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           createNavigateToNullParameterUsagesFix(parameter));
  }

  private void checkOverriders(@NotNull PsiMethod method,
                               @NotNull ProblemsHolder holder,
                               @NotNull Annotated annotated,
                               @NotNull NullableNotNullManager nullableManager) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
      boolean[] checkParameter = new boolean[parameters.length];
      boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
      boolean hasAnnotatedParameter = false;
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        checkParameter[i] = isNotNullNotInferred(parameter, false, false) &&
                                !hasInheritableNotNull(parameter) &&
                                !(parameter.getType() instanceof PsiPrimitiveType);
        hasAnnotatedParameter |= checkParameter[i];
      }
      boolean checkReturnType = annotated.isDeclaredNotNull && !hasInheritableNotNull(method) && !(method.getReturnType() instanceof PsiPrimitiveType);
      if (hasAnnotatedParameter || checkReturnType) {
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final boolean superMethodApplicable = AnnotationUtil.isAnnotatingApplicable(method, defaultNotNull);
        PsiMethod[] overridings =
          OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (shouldSkipOverriderAsGenerated(overriding)) continue;

          if (!methodQuickFixSuggested
              && checkReturnType
              && !isNotNullNotInferred(overriding, false, false)
              && (isNullableNotInferred(overriding, false) || !isNullableNotInferred(overriding, true))
              && AddAnnotationPsiFix.isAvailable(overriding, defaultNotNull)) {
            PsiIdentifier identifier = method.getNameIdentifier();//load tree
            NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(method);
            LOG.assertTrue(info != null);
            PsiAnnotation annotation = info.getAnnotation();
            final String[] annotationsToRemove = ArrayUtilRt.toStringArray(nullableManager.getNullables());

            LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(overriding, defaultNotNull)
                                ? new MyAnnotateMethodFix(defaultNotNull, annotationsToRemove)
                                : superMethodApplicable ? null : createChangeDefaultNotNullFix(nullableManager, method);

            PsiElement psiElement = annotation;
            if (!annotation.isPhysical()) {
              psiElement = identifier;
              if (psiElement == null) continue;
            }
            holder.registerProblem(psiElement, JavaAnalysisBundle.message("nullable.stuff.problems.overridden.methods.are.not.annotated"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   fix);
            methodQuickFixSuggested = true;
          }
          if (hasAnnotatedParameter) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) continue;
              PsiParameter parameter = psiParameters[i];
              if (checkParameter[i] &&
                  !isNotNullNotInferred(parameter, false, false) &&
                  !isNullableNotInferred(parameter, false) &&
                  AddAnnotationPsiFix.isAvailable(parameter, defaultNotNull)) {
                PsiIdentifier identifier = parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameters[i]);
                PsiElement psiElement = info == null ? null : info.getAnnotation();
                if (psiElement == null || !psiElement.isPhysical()) {
                  psiElement = identifier;
                  if (psiElement == null) continue;
                }
                LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, defaultNotNull)
                                    ? new AnnotateOverriddenMethodParameterFix(defaultNotNull, nullableManager.getDefaultNullable())
                                    : createChangeDefaultNotNullFix(nullableManager, parameters[i]);
                holder.registerProblem(psiElement,
                                       JavaAnalysisBundle.message("nullable.stuff.problems.overridden.method.parameters.are.not.annotated"),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       fix);
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  public static boolean shouldSkipOverriderAsGenerated(PsiMethod overriding) {
    if (Registry.is("idea.report.nullity.missing.in.generated.overriders")) return false;

    PsiFile file = overriding.getContainingFile();
    VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
    return virtualFile != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, overriding.getProject());
  }

  private static boolean isNotNullNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean skipExternal) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (!manager.isNotNull(owner, checkBases)) return false;
    if (DfaPsiUtil.getTypeNullability(getMemberType(owner)) == Nullability.NOT_NULL) return true;

    PsiAnnotation anno = manager.getNotNullAnnotation(owner, checkBases);
    if (anno == null || AnnotationUtil.isInferredAnnotation(anno)) return false;
    if (skipExternal && AnnotationUtil.isExternalAnnotation(anno)) return false;
    return true;
  }

  public static boolean isNullableNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    PsiAnnotation anno = manager.getNullableAnnotation(owner, checkBases);
    if (anno == null) return false;

    return DfaPsiUtil.getTypeNullability(getMemberType(owner)) == Nullability.NULLABLE || !AnnotationUtil.isInferredAnnotation(anno);
  }

  private static PsiType getMemberType(@NotNull PsiModifierListOwner owner) {
    return owner instanceof PsiMethod ? ((PsiMethod)owner).getReturnType() : owner instanceof PsiVariable ? ((PsiVariable)owner).getType() : null;
  }

  private static LocalQuickFix createChangeDefaultNotNullFix(NullableNotNullManager nullableManager, PsiModifierListOwner modifierListOwner) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, nullableManager.getNotNulls());
    if (annotation != null) {
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        JavaResolveResult resolveResult = referenceElement.advancedResolve(false);
        if (resolveResult.getElement() != null &&
            resolveResult.isValidResult() &&
            !nullableManager.getDefaultNotNull().equals(annotation.getQualifiedName())) {
          return new ChangeNullableDefaultsFix(annotation.getQualifiedName(), null, nullableManager);
        }
      }
    }
    return null;
  }

  private static void reportNullableNotNullConflict(final ProblemsHolder holder, final PsiModifierListOwner listOwner, final PsiAnnotation declaredNullable,
                                                    final PsiAnnotation declaredNotNull) {
    final String bothNullableNotNullMessage = JavaAnalysisBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict",
                                                                        getPresentableAnnoName(declaredNullable),
                                                                        getPresentableAnnoName(declaredNotNull));
    holder.registerProblem(declaredNotNull.isPhysical() ? declaredNotNull : listOwner.getNavigationElement(),
                           bothNullableNotNullMessage,
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(declaredNotNull, listOwner));
    holder.registerProblem(declaredNullable.isPhysical() ? declaredNullable : listOwner.getNavigationElement(),
                           bothNullableNotNullMessage,
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(declaredNullable, listOwner));
  }

  @Override
  public JComponent createOptionsPanel() {
    throw new RuntimeException("No UI in headless mode");
  }

  private static class MyAnnotateMethodFix extends AnnotateMethodFix {
    MyAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
      super(defaultNotNull, annotationsToRemove);
    }

    @NotNull
    @Override
    protected String getPreposition() {
      return "as";
    }

    @Override
    protected boolean annotateOverriddenMethods() {
      return true;
    }

    @Override
    protected boolean annotateSelf() {
      return false;
    }
  }
}
