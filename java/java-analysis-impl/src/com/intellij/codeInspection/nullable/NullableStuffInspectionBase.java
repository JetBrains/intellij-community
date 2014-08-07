/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.AddNotNullAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class NullableStuffInspectionBase extends BaseJavaBatchLocalInspectionTool {
  // deprecated fields remain to minimize changes to users inspection profiles (which are often located in version control).
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true; // remains for test
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLS_PASSED_TO_NON_ANNOTATED_METHOD = true;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.nullable.NullableStuffInspectionBase");

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitMethod(PsiMethod method) {
        if (!PsiUtil.isLanguageLevel5OrHigher(method)) return;
        checkNullableStuffForMethod(method, holder);
      }

      @Override public void visitField(PsiField field) {
        if (!PsiUtil.isLanguageLevel5OrHigher(field)) return;
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
            holder.registerProblem(annotationNameReferenceElement != null ? annotationNameReferenceElement : field.getNameIdentifier(),
                                   message,
                                   ProblemHighlightType.WEAK_WARNING,
                                   new ChangeNullableDefaultsFix(notNull, nullable, manager));
            return;
          }

          String propName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);
          final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
          final PsiMethod getter = PropertyUtil.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
          final PsiIdentifier nameIdentifier = getter == null ? null : getter.getNameIdentifier();
          if (nameIdentifier != null && nameIdentifier.isPhysical()) {
            if (PropertyUtil.isSimpleGetter(getter)) {
              AnnotateMethodFix getterAnnoFix = new AnnotateMethodFix(anno, ArrayUtil.toStringArray(annoToRemove)) {
                @Override
                public int shouldAnnotateBaseMethod(PsiMethod method, PsiMethod superMethod, Project project) {
                  return 1;
                }
              };
              if (REPORT_NOT_ANNOTATED_GETTER) {
                if (!manager.hasNullability(getter) && !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
                  holder.registerProblem(nameIdentifier, InspectionsBundle
                    .message("inspection.nullable.problems.annotated.field.getter.not.annotated", getPresentableAnnoName(field)),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
                }
              }
              if (annotated.isDeclaredNotNull && isNullableNotInferred(getter, false) ||
                  annotated.isDeclaredNullable && isNotNullNotInferred(getter, false)) {
                holder.registerProblem(nameIdentifier, InspectionsBundle.message(
                  "inspection.nullable.problems.annotated.field.getter.conflict", getPresentableAnnoName(field), getPresentableAnnoName(getter)),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
              }
            }
          }

          final PsiClass containingClass = field.getContainingClass();
          final PsiMethod setter = PropertyUtil.findPropertySetter(containingClass, propName, isStatic, false);
          if (setter != null && setter.isPhysical()) {
            final PsiParameter[] parameters = setter.getParameterList().getParameters();
            assert parameters.length == 1 : setter.getText();
            final PsiParameter parameter = parameters[0];
            LOG.assertTrue(parameter != null, setter.getText());
            AddAnnotationPsiFix addAnnoFix = new AddAnnotationPsiFix(anno, parameter, PsiNameValuePair.EMPTY_ARRAY, ArrayUtil.toStringArray(annoToRemove));
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
              if (annotated.isDeclaredNotNull && isNullableNotInferred(parameter, false) ||
                  annotated.isDeclaredNullable && isNotNullNotInferred(parameter, false)) {
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

          List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
          if (annotated.isDeclaredNotNull && initializers.isEmpty()) {
            final PsiAnnotation annotation = AnnotationUtil.findAnnotation(field, manager.getNotNulls());
            if (annotation != null) {
              holder.registerProblem(annotation.isPhysical() ? annotation : field.getNameIdentifier(),
                                     "Not-null fields must be initialized",
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }

          for (PsiExpression rhs : initializers) {
            if (rhs instanceof PsiReferenceExpression) {
              PsiElement target = ((PsiReferenceExpression)rhs).resolve();
              if (target instanceof PsiParameter) {
                PsiParameter parameter = (PsiParameter)target;
                AddAnnotationPsiFix fix = new AddAnnotationPsiFix(anno, parameter, PsiNameValuePair.EMPTY_ARRAY, ArrayUtil.toStringArray(annoToRemove));
                if (REPORT_NOT_ANNOTATED_GETTER && !manager.hasNullability(parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
                  final PsiIdentifier nameIdentifier2 = parameter.getNameIdentifier();
                  assert nameIdentifier2 != null : parameter;
                  holder.registerProblem(nameIdentifier2, InspectionsBundle
                    .message("inspection.nullable.problems.annotated.field.constructor.parameter.not.annotated",
                             getPresentableAnnoName(field)),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
                  continue;
                }
                if (annotated.isDeclaredNullable && isNotNullNotInferred(parameter, false)) {
                  boolean usedAsQualifier = !ReferencesSearch.search(parameter).forEach(new Processor<PsiReference>() {
                    @Override
                    public boolean process(PsiReference reference) {
                      final PsiElement element = reference.getElement();
                      return !(element instanceof PsiReferenceExpression && element.getParent() instanceof PsiReferenceExpression);
                    }
                  });
                  if (!usedAsQualifier) {
                    final PsiIdentifier nameIdentifier2 = parameter.getNameIdentifier();
                    assert nameIdentifier2 != null : parameter;
                    holder.registerProblem(nameIdentifier2, InspectionsBundle.message(
                      "inspection.nullable.problems.annotated.field.constructor.parameter.conflict", getPresentableAnnoName(field),
                      getPresentableAnnoName(parameter)),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           fix);
                  }
                }

              }
            }
          }
        }
      }

      private void assertValidElement(PsiMethod setter, PsiParameter parameter, PsiIdentifier nameIdentifier1) {
        LOG.assertTrue(nameIdentifier1 != null && nameIdentifier1.isPhysical(), setter.getText());
        LOG.assertTrue(parameter.isPhysical(), setter.getText());
      }

      @Override public void visitParameter(PsiParameter parameter) {
        if (!PsiUtil.isLanguageLevel5OrHigher(parameter)) return;
        check(parameter, holder, parameter.getType());
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        if (!AnnotationUtil.NOT_NULL.equals(annotation.getQualifiedName())) return;

        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("exception");
        if (value instanceof PsiClassObjectAccessExpression) {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(((PsiClassObjectAccessExpression)value).getOperand().getType());
          if (psiClass != null && !hasStringConstructor(psiClass)) {
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
    };
  }

  @NotNull
  private static String getPresentableAnnoName(@NotNull PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    String anno = manager.getNotNull(owner);
    return StringUtil.getShortName(anno != null ? anno : StringUtil.notNullize(manager.getNullable(owner), "???"));
  }

  private static String getPresentableAnnoName(@NotNull PsiAnnotation annotation) {
    return StringUtil.getShortName(StringUtil.notNullize(annotation.getQualifiedName(), "???"));
  }

  private static class Annotated {
    private final boolean isDeclaredNotNull;
    private final boolean isDeclaredNullable;

    private Annotated(final boolean isDeclaredNotNull, final boolean isDeclaredNullable) {
      this.isDeclaredNotNull = isDeclaredNotNull;
      this.isDeclaredNullable = isDeclaredNullable;
    }
  }
  private static Annotated check(final PsiModifierListOwner parameter, final ProblemsHolder holder, PsiType type) {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
    PsiAnnotation isDeclaredNotNull = AnnotationUtil.findAnnotation(parameter, manager.getNotNulls());
    PsiAnnotation isDeclaredNullable = AnnotationUtil.findAnnotation(parameter, manager.getNullables());
    if (isDeclaredNullable != null && isDeclaredNotNull != null) {
      reportNullableNotNullConflict(holder, parameter, isDeclaredNullable, isDeclaredNotNull);
    }
    if ((isDeclaredNotNull != null || isDeclaredNullable != null) && type != null && TypeConversionUtil.isPrimitive(type.getCanonicalText())) {
      PsiAnnotation annotation = isDeclaredNotNull == null ? isDeclaredNullable : isDeclaredNotNull;
      reportPrimitiveType(holder, annotation, annotation, parameter);
    }
    return new Annotated(isDeclaredNotNull != null,isDeclaredNullable != null);
  }

  private static void reportPrimitiveType(final ProblemsHolder holder, final PsiElement psiElement, final PsiAnnotation annotation,
                                          final PsiModifierListOwner listOwner) {
    holder.registerProblem(psiElement.isPhysical() ? psiElement : listOwner.getNavigationElement(),
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

  private void checkNullableStuffForMethod(PsiMethod method, final ProblemsHolder holder) {
    Annotated annotated = check(method, holder, method.getReturnType());

    PsiParameter[] parameters = method.getParameterList().getParameters();

    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
    boolean reported_not_annotated_method_overrides_notnull = false;
    boolean reported_nullable_method_overrides_notnull = false;
    boolean[] reported_notnull_parameter_overrides_nullable = new boolean[parameters.length];
    boolean[] reported_not_annotated_parameter_overrides_notnull = new boolean[parameters.length];

    final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(holder.getProject());
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (!reported_nullable_method_overrides_notnull
          && REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE
          && annotated.isDeclaredNullable
          && isNotNullNotInferred(superMethod, true)) {
        reported_nullable_method_overrides_notnull = true;
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, nullableManager.getNullables(), true);
        holder.registerProblem(annotation != null ? annotation : method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull", getPresentableAnnoName(method), getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      if (!reported_not_annotated_method_overrides_notnull
          && REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL
          && !annotated.isDeclaredNullable
          && !annotated.isDeclaredNotNull
          && isNotNullNotInferred(superMethod, true)) {
        reported_not_annotated_method_overrides_notnull = true;
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final String[] annotationsToRemove = ArrayUtil.toStringArray(nullableManager.getNullables());
        final LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(method, defaultNotNull)
                                  ? createAnnotateMethodFix(defaultNotNull, annotationsToRemove)
                                  : createChangeDefaultNotNullFix(nullableManager, superMethod);
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.method.overrides.NotNull", getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               wrapFix(fix));
      }
      if (REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE || REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL) {
        PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
        if (superParameters.length != parameters.length) {
          continue;
        }
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiParameter superParameter = superParameters[i];
          if (!reported_notnull_parameter_overrides_nullable[i] && REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE &&
              isNotNullNotInferred(parameter, false) &&
              isNullableNotInferred(superParameter, false)) {
            reported_notnull_parameter_overrides_nullable[i] = true;
            final PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, nullableManager.getNotNulls(), true);
            holder.registerProblem(annotation != null ? annotation : parameter.getNameIdentifier(),
                                   InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
                                                             getPresentableAnnoName(parameter),
                                                             getPresentableAnnoName(superParameter)),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
          if (!reported_not_annotated_parameter_overrides_notnull[i] && REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL) {
            if (!nullableManager.hasNullability(parameter) && isNotNullNotInferred(superParameter, false)) {
              reported_not_annotated_parameter_overrides_notnull[i] = true;
              final LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, nullableManager.getDefaultNotNull())
                                        ? new AddNotNullAnnotationFix(parameter)
                                        : createChangeDefaultNotNullFix(nullableManager, superParameter);
              holder.registerProblem(parameter.getNameIdentifier(),
                                     InspectionsBundle.message("inspection.nullable.problems.parameter.overrides.NotNull", getPresentableAnnoName(superParameter)),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     wrapFix(fix));
            }
          }
        }
      }
    }

    if (REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
      boolean[] parameterAnnotated = new boolean[parameters.length];
      boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
      boolean hasAnnotatedParameter = false;
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        parameterAnnotated[i] = isNotNullNotInferred(parameter, false);
        hasAnnotatedParameter |= parameterAnnotated[i];
      }
      if (hasAnnotatedParameter || annotated.isDeclaredNotNull) {
        PsiManager manager = method.getManager();
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final boolean superMethodApplicable = AnnotationUtil.isAnnotatingApplicable(method, defaultNotNull);
        PsiMethod[] overridings =
          OverridingMethodsSearch.search(method, GlobalSearchScope.allScope(manager.getProject()), true).toArray(PsiMethod.EMPTY_ARRAY);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (!manager.isInProject(overriding)) continue;

          final boolean applicable = AnnotationUtil.isAnnotatingApplicable(overriding, defaultNotNull);
          if (!methodQuickFixSuggested
              && annotated.isDeclaredNotNull
              && !isNotNullNotInferred(overriding, false)
              && (isNullableNotInferred(overriding, false) || !isNullableNotInferred(overriding, true))) {
            method.getNameIdentifier(); //load tree
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
              psiElement = method.getNameIdentifier();
              if (psiElement == null) continue;
            }
            holder.registerProblem(psiElement, InspectionsBundle.message("nullable.stuff.problems.overridden.methods.are.not.annotated"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   wrapFix(fix));
            methodQuickFixSuggested = true;
          }
          if (hasAnnotatedParameter) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) continue;
              PsiParameter parameter = psiParameters[i];
              if (parameterAnnotated[i] && !isNotNullNotInferred(parameter, false) && !isNullableNotInferred(parameter, false)) {
                parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameters[i], nullableManager.getNotNulls());
                PsiElement psiElement = annotation;
                if (annotation == null || !annotation.isPhysical()) {
                  psiElement = parameters[i].getNameIdentifier();
                  if (psiElement == null) continue;
                }
                holder.registerProblem(psiElement,
                                       InspectionsBundle.message("nullable.stuff.problems.overridden.method.parameters.are.not.annotated"),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       wrapFix(!applicable
                                               ? createChangeDefaultNotNullFix(nullableManager, parameters[i])
                                               : new AnnotateOverriddenMethodParameterFix(defaultNotNull,
                                                                                          nullableManager.getDefaultNullable())));
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  private static boolean isNotNullNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (!manager.isNotNull(owner, checkBases)) return false;

    PsiAnnotation anno = manager.getNotNullAnnotation(owner, checkBases);
    return !(anno != null && AnnotationUtil.isInferredAnnotation(anno));
  }

  private static boolean isNullableNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (!manager.isNullable(owner, checkBases)) return false;

    PsiAnnotation anno = manager.getNullableAnnotation(owner, checkBases);
    return !(anno != null && AnnotationUtil.isInferredAnnotation(anno));
  }

  @NotNull
  private static LocalQuickFix[] wrapFix(LocalQuickFix fix) {
    if (fix == null) return LocalQuickFix.EMPTY_ARRAY;
    return new LocalQuickFix[]{fix};
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

  protected AnnotateMethodFix createAnnotateMethodFix(final String defaultNotNull, final String[] annotationsToRemove) {
    return new AnnotateMethodFix(defaultNotNull, annotationsToRemove);
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

    @Override
    protected boolean annotateOverriddenMethods() {
      return true;
    }

    @Override
    public int shouldAnnotateBaseMethod(PsiMethod method, PsiMethod superMethod, Project project) {
      return 1;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("annotate.overridden.methods.as.notnull", ClassUtil.extractClassName(myAnnotation));
    }
  }
}
