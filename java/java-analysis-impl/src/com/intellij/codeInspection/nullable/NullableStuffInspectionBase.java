// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.AddNotNullAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
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
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean IGNORE_EXTERNAL_SUPER_NOTNULL;
  @SuppressWarnings({"WeakerAccess"}) public boolean REQUIRE_NOTNULL_FIELDS_INITIALIZED = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true; // remains for test
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLS_PASSED_TO_NON_ANNOTATED_METHOD = true;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.nullable.NullableStuffInspectionBase");

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

        if (REQUIRE_NOTNULL_FIELDS_INITIALIZED && !annotated.isDeclaredNullable) {
          checkNotNullFieldsInitialized(field, manager, holder);
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
            //noinspection DialogTitleCapitalization
            holder.registerProblem(value,
                                   "Custom exception class should have a constructor with a single message parameter of String type",
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

          }
        }
      }

      private boolean hasStringConstructor(PsiClass aClass) {
        for (PsiMethod method : aClass.getConstructors()) {
          PsiParameterList list = method.getParameterList();
          if (list.getParametersCount() == 1 &&
              list.getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
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
                  Nullness.NOT_NULL && DfaPsiUtil.getTypeNullability(typeArguments[i].getType()) != Nullness.NOT_NULL) {
                holder.registerProblem(typeArguments[i],
                                       "Non-null type argument is expected",
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
        if (isNullableNotNullCollectionConflict(errorElement, expectedType, assignedType, new HashSet<>())) {
          holder.registerProblem(errorElement,
                                 "Assigning a collection of nullable elements into a collection of non-null elements",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

        }
      }

      private boolean isNullableNotNullCollectionConflict(PsiElement place,
                                                          @Nullable PsiType expectedType,
                                                          @Nullable PsiType assignedType,
                                                          @NotNull Set<Couple<PsiType>> visited) {
        if (!visited.add(Couple.of(expectedType, assignedType))) return false;

        if (isNullityConflict(JavaGenericsUtil.getCollectionItemType(expectedType, place.getResolveScope()),
                              JavaGenericsUtil.getCollectionItemType(assignedType, place.getResolveScope()))) {
          return true;
        }

        for (int i = 0; i <= 1; i++) {
          PsiType expectedArg = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
          PsiType assignedArg = PsiUtil.substituteTypeParameter(assignedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
          if (isNullityConflict(expectedArg, assignedArg) ||
              expectedArg != null && assignedArg != null && isNullableNotNullCollectionConflict(place, expectedArg, assignedArg, visited)) {
            return true;
          }
        }

        return false;
      }

      private boolean isNullityConflict(PsiType expected, PsiType assigned) {
        return DfaPsiUtil.getTypeNullability(expected) == Nullness.NOT_NULL && DfaPsiUtil.getTypeNullability(assigned) == Nullness.NULLABLE;
      }
    };
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
                             InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull",
                                                       getPresentableAnnoName(targetMethod), getPresentableAnnoName(superMethod)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    else if (isNonAnnotatedOverridingNotNull(targetMethod, superMethod)) {
      holder.registerProblem(refName,
                             "Not annotated method is used as an override for a method annotated with " + getPresentableAnnoName(superMethod),
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
      final PsiAnnotation notNull = AnnotationUtil.findAnnotation(field, manager.getNotNulls());
      final PsiAnnotation nullable = AnnotationUtil.findAnnotation(field, manager.getNullables());
      final PsiAnnotation annotation;
      String message = "Not \'";
      if (annotated.isDeclaredNullable) {
        message += nullable.getQualifiedName();
        annotation = nullable;
      } else {
        message += notNull.getQualifiedName();
        annotation = notNull;
      }
      message += "\' but \'" + anno + "\' would be used for code generation.";
      final PsiJavaCodeReferenceElement annotationNameReferenceElement = annotation.getNameReferenceElement();
      holder.registerProblem(annotationNameReferenceElement != null && annotationNameReferenceElement.isPhysical() ? annotationNameReferenceElement : field.getNameIdentifier(),
                             message,
                             ProblemHighlightType.WEAK_WARNING,
                             new ChangeNullableDefaultsFix(notNull, nullable, manager));
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
      if (PropertyUtil.isSimpleGetter(getter)) {
        AnnotateMethodFix getterAnnoFix = new AnnotateMethodFix(anno, ArrayUtil.toStringArray(annoToRemove));
        if (REPORT_NOT_ANNOTATED_GETTER) {
          if (!manager.hasNullability(getter) && !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
            holder.registerProblem(nameIdentifier, InspectionsBundle
                                     .message("inspection.nullable.problems.annotated.field.getter.not.annotated", getPresentableAnnoName(field)),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
          }
        }
        if (annotated.isDeclaredNotNull && isNullableNotInferred(getter, false) ||
            annotated.isDeclaredNullable && isNotNullNotInferred(getter, false, false)) {
          holder.registerProblem(nameIdentifier, InspectionsBundle.message(
                                   "inspection.nullable.problems.annotated.field.getter.conflict", getPresentableAnnoName(field), getPresentableAnnoName(getter)),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
        }
      }
    }

    final PsiClass containingClass = field.getContainingClass();
    final PsiMethod setter = PropertyUtilBase.findPropertySetter(containingClass, propName, isStatic, false);
    if (setter != null && setter.isPhysical() && PropertyUtil.isSimpleSetter(setter)) {
      final PsiParameter[] parameters = setter.getParameterList().getParameters();
      assert parameters.length == 1 : setter.getText();
      final PsiParameter parameter = parameters[0];
      LOG.assertTrue(parameter != null, setter.getText());
      AddAnnotationPsiFix addAnnoFix = createAddAnnotationFix(anno, annoToRemove, parameter);
      if (REPORT_NOT_ANNOTATED_GETTER && !manager.hasNullability(parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
        final PsiIdentifier nameIdentifier1 = parameter.getNameIdentifier();
        assertValidElement(setter, parameter, nameIdentifier1);
        holder.registerProblem(nameIdentifier1,
                               InspectionsBundle.message("inspection.nullable.problems.annotated.field.setter.parameter.not.annotated",
                                                         getPresentableAnnoName(field)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               addAnnoFix);
      }
      if (PropertyUtil.isSimpleSetter(setter)) {
        if (annotated.isDeclaredNotNull && isNullableNotInferred(parameter, false)) {
          final PsiIdentifier nameIdentifier1 = parameter.getNameIdentifier();
          assertValidElement(setter, parameter, nameIdentifier1);
          holder.registerProblem(nameIdentifier1, InspectionsBundle.message(
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
    return new AddAnnotationPsiFix(anno, parameter, PsiNameValuePair.EMPTY_ARRAY, ArrayUtil.toStringArray(annoToRemove));
  }

  private static void assertValidElement(PsiMethod setter, PsiParameter parameter, PsiIdentifier nameIdentifier1) {
    LOG.assertTrue(nameIdentifier1 != null && nameIdentifier1.isPhysical(), setter.getText());
    LOG.assertTrue(parameter.isPhysical(), setter.getText());
  }

  private static void checkNotNullFieldsInitialized(PsiField field, NullableNotNullManager manager, @NotNull ProblemsHolder holder) {
    PsiAnnotation annotation = manager.getNotNullAnnotation(field, false);
    if (annotation == null || HighlightControlFlowUtil.isFieldInitializedAfterObjectConstruction(field)) return;

    boolean byDefault = manager.isContainerAnnotation(annotation);
    PsiJavaCodeReferenceElement name = annotation.getNameReferenceElement();
    holder.registerProblem(annotation.isPhysical() && !byDefault ? annotation : field.getNameIdentifier(),
                           (byDefault && name != null ? "@" + name.getReferenceName() : "Not-null") + " fields must be initialized");
  }

  private void checkConstructorParameters(PsiField field,
                                          Annotated annotated,
                                          NullableNotNullManager manager,
                                          String anno, List<String> annoToRemove, @NotNull ProblemsHolder holder) {
    List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
    if (initializers.isEmpty()) return;

    List<PsiParameter> notNullParams = ContainerUtil.newArrayList();

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
                InspectionsBundle.message("inspection.nullable.problems.annotated.field.constructor.parameter.not.annotated", getPresentableAnnoName(field)),
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
      holder.registerProblem(nameIdentifier, "@" + getPresentableAnnoName(field) + " field is always initialized not-null",
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new AddNotNullAnnotationFix(field));
    }
  }

  private static boolean isConstructorParameter(@Nullable PsiElement parameter) {
    return parameter instanceof PsiParameter && psiElement(PsiParameterList.class).withParent(psiMethod().constructor(true)).accepts(parameter.getParent());
  }

  @NotNull
  private static String getPresentableAnnoName(@NotNull PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    Set<String> names = ContainerUtil.newHashSet(manager.getNullables());
    names.addAll(manager.getNotNulls());

    PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(owner, names);
    if (annotation != null) return getPresentableAnnoName(annotation);

    String anno = manager.getNotNull(owner);
    return StringUtil.getShortName(anno != null ? anno : StringUtil.notNullize(manager.getNullable(owner), "???"));
  }

  public static String getPresentableAnnoName(@NotNull PsiAnnotation annotation) {
    return StringUtil.getShortName(StringUtil.notNullize(annotation.getQualifiedName(), "???"));
  }

  private static class Annotated {
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
      return new Annotated(AnnotationUtil.findAnnotation(owner, manager.getNotNulls()),
                           AnnotationUtil.findAnnotation(owner, manager.getNullables()));
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
      reportPrimitiveType(holder, annotation, listOwner);
    }
    if (listOwner instanceof PsiParameter) {
      checkLoopParameterNullability(holder, notNull, nullable, DfaPsiUtil.inferParameterNullability((PsiParameter)listOwner));
    }
  }

  private static void checkLoopParameterNullability(ProblemsHolder holder, @Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable, Nullness expectedNullability) {
    if (notNull != null && expectedNullability == Nullness.NULLABLE) {
      holder.registerProblem(notNull, "Parameter can be null",
                             new RemoveAnnotationQuickFix(notNull, null));
    }
    else if (nullable != null && expectedNullability == Nullness.NOT_NULL) {
      holder.registerProblem(nullable, "Parameter is always not-null",
                             new RemoveAnnotationQuickFix(nullable, null));
    }
  }

  private static void reportPrimitiveType(ProblemsHolder holder, PsiAnnotation annotation,
                                          @Nullable PsiModifierListOwner listOwner) {
    holder.registerProblem(!annotation.isPhysical() && listOwner != null ? listOwner.getNavigationElement() : annotation,
                           InspectionsBundle.message("inspection.nullable.problems.primitive.type.annotation"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(annotation, listOwner));
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.nullable.problems.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "NullableProblems";
  }

  private void checkNullableStuffForMethod(PsiMethod method, final ProblemsHolder holder, boolean isOnFly) {
    Annotated annotated = check(method, holder, method.getReturnType());

    List<PsiMethod> superMethods = ContainerUtil.map(
      method.findSuperMethodSignaturesIncludingStatic(true), signature -> signature.getMethod());

    final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(holder.getProject());

    checkSupers(method, holder, annotated, superMethods);
    checkParameters(method, holder, superMethods, nullableManager, isOnFly);
    checkOverriders(method, holder, annotated, nullableManager);
  }

  private void checkSupers(PsiMethod method,
                           ProblemsHolder holder,
                           Annotated annotated,
                           List<PsiMethod> superMethods) {
    for (PsiMethod superMethod : superMethods) {
      if (isNullableOverridingNotNull(annotated, superMethod)) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, getNullityManager(method).getNullables(), true);
        holder.registerProblem(annotation != null ? annotation : method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull",
                                                         getPresentableAnnoName(method), getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        break;
      }

      if (isNonAnnotatedOverridingNotNull(method, superMethod)) {
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle
                                 .message("inspection.nullable.problems.method.overrides.NotNull", getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               createFixForNonAnnotatedOverridesNotNull(method, superMethod));
        break;
      }
    }
  }

  private static NullableNotNullManager getNullityManager(PsiMethod method) {
    return NullableNotNullManager.getInstance(method.getProject());
  }

  private static LocalQuickFix createFixForNonAnnotatedOverridesNotNull(PsiMethod method,
                                                                        PsiMethod superMethod) {
    NullableNotNullManager nullableManager = getNullityManager(method);
    return AnnotationUtil.isAnnotatingApplicable(method, nullableManager.getDefaultNotNull())
                              ? new AddNotNullAnnotationFix(method)
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
                               List<PsiMethod> superMethods,
                               NullableNotNullManager nullableManager,
                               boolean isOnFly) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];

      List<PsiParameter> superParameters = ContainerUtil.newArrayList();
      for (PsiMethod superMethod : superMethods) {
        PsiParameter[] _superParameters = superMethod.getParameterList().getParameters();
        if (_superParameters.length == parameters.length) {
          superParameters.add(_superParameters[i]);
        }
      }

      PsiParameter nullableSuper = findNullableSuperForNotNullParameter(parameter, superParameters);
      if (nullableSuper != null) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, nullableManager.getNotNulls(), true);
        holder.registerProblem(annotation != null ? annotation : parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
                                                         getPresentableAnnoName(parameter),
                                                         getPresentableAnnoName(nullableSuper)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      PsiParameter notNullSuper = findNotNullSuperForNonAnnotatedParameter(nullableManager, parameter, superParameters);
      if (notNullSuper != null) {
        LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, nullableManager.getDefaultNotNull())
                            ? new AddNotNullAnnotationFix(parameter)
                            : createChangeDefaultNotNullFix(nullableManager, notNullSuper);
        holder.registerProblem(parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.parameter.overrides.NotNull", getPresentableAnnoName(notNullSuper)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               fix);
      }
      if (isNotNullParameterOverridingNonAnnotated(nullableManager, parameter, superParameters)) {
        PsiAnnotation notNullAnnotation = nullableManager.getNotNullAnnotation(parameter, false);
        assert notNullAnnotation != null;
        boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
        final LocalQuickFix fix = physical ? new RemoveAnnotationQuickFix(notNullAnnotation, parameter) : null;
        holder.registerProblem(physical ? notNullAnnotation : parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.not.annotated", getPresentableAnnoName(parameter)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               fix);
      }

      checkNullLiteralArgumentOfNotNullParameterUsages(method, holder, nullableManager, isOnFly, i, parameter);
    }
  }

  @Nullable
  private PsiParameter findNotNullSuperForNonAnnotatedParameter(NullableNotNullManager nullableManager,
                                                                PsiParameter parameter,
                                                                List<PsiParameter> superParameters) {
    return REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !nullableManager.hasNullability(parameter) 
           ? ContainerUtil.find(superParameters, 
                                sp -> isNotNullNotInferred(sp, false, IGNORE_EXTERNAL_SUPER_NOTNULL) && !hasInheritableNotNull(sp)) 
           : null;
  }

  @Nullable
  private PsiParameter findNullableSuperForNotNullParameter(PsiParameter parameter, List<PsiParameter> superParameters) {
    return REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && isNotNullNotInferred(parameter, false, false) 
           ? ContainerUtil.find(superParameters, sp -> isNullableNotInferred(sp, false)) 
           : null;
  }

  private boolean isNotNullParameterOverridingNonAnnotated(NullableNotNullManager nullableManager,
                                                           PsiParameter parameter,
                                                           List<PsiParameter> superParameters) {
    return REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED 
           && isNotNullNotInferred(parameter, false, false) 
           && ContainerUtil.exists(superParameters, sp -> !nullableManager.hasNullability(sp));
  }

  private void checkNullLiteralArgumentOfNotNullParameterUsages(PsiMethod method,
                                                                ProblemsHolder holder,
                                                                NullableNotNullManager nullableManager,
                                                                boolean isOnFly,
                                                                int parameterIdx,
                                                                PsiParameter parameter) {
    if (REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER && isOnFly && isNotNullNotInferred(parameter, false, false)) {
      PsiAnnotation notNullAnnotation = nullableManager.getNotNullAnnotation(parameter, false);
      if (JavaNullMethodArgumentUtil.hasNullArgument(method, parameterIdx)) {
        boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
        holder.registerProblem(physical ? notNullAnnotation : parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.receives.null.literal", getPresentableAnnoName(parameter)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               createNavigateToNullParameterUsagesFix(parameter));
      }
    }
  }

  private void checkOverriders(@NotNull PsiMethod method,
                               @NotNull ProblemsHolder holder,
                               @NotNull Annotated annotated,
                               @NotNull NullableNotNullManager nullableManager) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
      boolean[] parameterAnnotated = new boolean[parameters.length];
      boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
      boolean hasAnnotatedParameter = false;
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        parameterAnnotated[i] = isNotNullNotInferred(parameter, false, false) && !hasInheritableNotNull(parameter);
        hasAnnotatedParameter |= parameterAnnotated[i];
      }
      if (hasAnnotatedParameter || annotated.isDeclaredNotNull && !hasInheritableNotNull(method)) {
        PsiManager manager = method.getManager();
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final boolean superMethodApplicable = AnnotationUtil.isAnnotatingApplicable(method, defaultNotNull);
        PsiMethod[] overridings =
          OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (!manager.isInProject(overriding)) continue;

          final boolean applicable = AnnotationUtil.isAnnotatingApplicable(overriding, defaultNotNull);
          boolean ableToAddNotNullAnnotation = AddAnnotationPsiFix.isAvailable(overriding, defaultNotNull);
          if (!methodQuickFixSuggested
              && annotated.isDeclaredNotNull
              && !isNotNullNotInferred(overriding, false, false)
              && (isNullableNotInferred(overriding, false) || !isNullableNotInferred(overriding, true))
              && ableToAddNotNullAnnotation) {
            PsiIdentifier identifier = method.getNameIdentifier();//load tree
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, nullableManager.getNotNulls());
            final String[] annotationsToRemove = ArrayUtil.toStringArray(nullableManager.getNullables());

            final LocalQuickFix fix;
            if (applicable) {
              fix = new MyAnnotateMethodFix(defaultNotNull, annotationsToRemove);
            }
            else {
              fix = superMethodApplicable ? null : createChangeDefaultNotNullFix(nullableManager, method);
            }

            PsiElement psiElement = annotation;
            if (!annotation.isPhysical()) {
              psiElement = identifier;
              if (psiElement == null) continue;
            }
            holder.registerProblem(psiElement, InspectionsBundle.message("nullable.stuff.problems.overridden.methods.are.not.annotated"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   fix);
            methodQuickFixSuggested = true;
          }
          if (hasAnnotatedParameter && ableToAddNotNullAnnotation) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) continue;
              PsiParameter parameter = psiParameters[i];
              if (parameterAnnotated[i] && !isNotNullNotInferred(parameter, false, false) && !isNullableNotInferred(parameter, false)) {
                PsiIdentifier identifier = parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameters[i], nullableManager.getNotNulls());
                PsiElement psiElement = annotation;
                if (annotation == null || !annotation.isPhysical()) {
                  psiElement = identifier;
                  if (psiElement == null) continue;
                }
                holder.registerProblem(psiElement,
                                       InspectionsBundle.message("nullable.stuff.problems.overridden.method.parameters.are.not.annotated"),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       !applicable
                                       ? createChangeDefaultNotNullFix(nullableManager, parameters[i])
                                       : new AnnotateOverriddenMethodParameterFix(defaultNotNull,
                                                                                  nullableManager.getDefaultNullable()));
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  private static boolean isNotNullNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean skipExternal) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (!manager.isNotNull(owner, checkBases)) return false;
    if (DfaPsiUtil.getTypeNullability(getMemberType(owner)) == Nullness.NOT_NULL) return true;

    PsiAnnotation anno = manager.getNotNullAnnotation(owner, checkBases);
    if (anno == null || AnnotationUtil.isInferredAnnotation(anno)) return false;
    if (skipExternal && AnnotationUtil.isExternalAnnotation(anno)) return false;
    return true;
  }

  public static boolean isNullableNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (!manager.isNullable(owner, checkBases)) return false;
    if (DfaPsiUtil.getTypeNullability(getMemberType(owner)) == Nullness.NULLABLE) return true;

    PsiAnnotation anno = manager.getNullableAnnotation(owner, checkBases);
    return !(anno != null && AnnotationUtil.isInferredAnnotation(anno));
  }

  private static PsiType getMemberType(@NotNull PsiModifierListOwner owner) {
    return owner instanceof PsiMethod ? ((PsiMethod)owner).getReturnType() : owner instanceof PsiVariable ? ((PsiVariable)owner).getType() : null;
  }

  private static LocalQuickFix createChangeDefaultNotNullFix(NullableNotNullManager nullableManager, PsiModifierListOwner modifierListOwner) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, nullableManager.getNotNulls());
    if (annotation != null) {
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null && referenceElement.resolve() != null) {
        return new ChangeNullableDefaultsFix(annotation.getQualifiedName(), null, nullableManager);
      }
    }
    return null;
  }

  private static void reportNullableNotNullConflict(final ProblemsHolder holder, final PsiModifierListOwner listOwner, final PsiAnnotation declaredNullable,
                                                    final PsiAnnotation declaredNotNull) {
    final String bothNullableNotNullMessage = InspectionsBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict",
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
    public MyAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
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
