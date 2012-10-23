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
import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.impl.AddNotNullAnnotationFix;
import com.intellij.codeInsight.intention.impl.AddNullableAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class NullableStuffInspection extends BaseLocalInspectionTool {
  // deprecated fields remain to minimize changes to users inspection profiles (which are often located in version control).
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  @Deprecated @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true; // remains for test
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLS_PASSED_TO_NON_ANNOTATED_METHOD = true;
  
  private static final Logger LOG = Logger.getInstance("#" + NullableStuffInspection.class.getName()); 

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
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(field.getProject());
        if (annotated.isDeclaredNotNull ^ annotated.isDeclaredNullable) {
          final String anno = annotated.isDeclaredNotNull ? manager.getDefaultNotNull() : manager.getDefaultNullable();
          final List<String> annoToRemove = annotated.isDeclaredNotNull ? manager.getNullables() : manager.getNotNulls();

          final String propName = JavaCodeStyleManager.getInstance(field.getProject()).variableNameToPropertyName(field.getName(),
                                                                                                                  VariableKind.FIELD);
          final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
          final PsiMethod getter = PropertyUtil.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
          final String nullableSimpleName = StringUtil.getShortName(manager.getDefaultNullable());
          final String notNullSimpleName = StringUtil.getShortName(manager.getDefaultNotNull());
          final PsiIdentifier nameIdentifier = getter == null ? null : getter.getNameIdentifier();
          if (nameIdentifier != null && nameIdentifier.isPhysical()) {
            if (PropertyUtils.isSimpleGetter(getter)) {
              if (REPORT_NOT_ANNOTATED_GETTER) {
                if (!AnnotationUtil.isAnnotated(getter, manager.getAllAnnotations(), false, false) &&
                    !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
                  holder.registerProblem(nameIdentifier, InspectionsBundle
                    .message("inspection.nullable.problems.annotated.field.getter.not.annotated", StringUtil.getShortName(anno)),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new AnnotateMethodFix(anno, ArrayUtil.toStringArray(annoToRemove)));
                }
              }
              if (annotated.isDeclaredNotNull && manager.isNullable(getter, false)) {
                holder.registerProblem(nameIdentifier, InspectionsBundle.message(
                  "inspection.nullable.problems.annotated.field.getter.conflict", StringUtil.getShortName(anno), nullableSimpleName),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new AnnotateMethodFix(anno, ArrayUtil.toStringArray(annoToRemove)));
              } else if (annotated.isDeclaredNullable && manager.isNotNull(getter, false)) {
                holder.registerProblem(nameIdentifier, InspectionsBundle.message(
                  "inspection.nullable.problems.annotated.field.getter.conflict", StringUtil.getShortName(anno), notNullSimpleName),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new AnnotateMethodFix(anno, ArrayUtil.toStringArray(annoToRemove)));
              }
            }
          }

          final PsiClass containingClass = field.getContainingClass();
          final PsiMethod setter = PropertyUtil.findPropertySetter(containingClass, propName, isStatic, false);
          if (setter != null) {
            final PsiParameter[] parameters = setter.getParameterList().getParameters();
            assert parameters.length == 1 : setter.getText();
            final PsiParameter parameter = parameters[0];
            LOG.assertTrue(parameter != null, setter.getText());
            if (REPORT_NOT_ANNOTATED_GETTER && !AnnotationUtil.isAnnotated(parameter, manager.getAllAnnotations(), false, false) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
              final PsiIdentifier nameIdentifier1 = parameter.getNameIdentifier();
              assertValidElement(setter, parameter, nameIdentifier1);
              holder.registerProblem(nameIdentifier1,
                                     InspectionsBundle.message("inspection.nullable.problems.annotated.field.setter.parameter.not.annotated",
                                                               StringUtil.getShortName(anno)),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     new AddAnnotationFix(anno, parameter, ArrayUtil.toStringArray(annoToRemove)));
            }
            if (PropertyUtils.isSimpleSetter(setter)) {
              if (annotated.isDeclaredNotNull && manager.isNullable(parameter, false)) {
                final PsiIdentifier nameIdentifier1 = parameter.getNameIdentifier();
                assertValidElement(setter, parameter, nameIdentifier1);
                holder.registerProblem(nameIdentifier1, InspectionsBundle.message(
                                       "inspection.nullable.problems.annotated.field.setter.parameter.conflict",
                                       StringUtil.getShortName(anno), nullableSimpleName),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       new AddAnnotationFix(anno, parameter, ArrayUtil.toStringArray(annoToRemove)));
              }
              else if (annotated.isDeclaredNullable && manager.isNotNull(parameter, false)) {
                final PsiIdentifier nameIdentifier1 = parameter.getNameIdentifier();
                assertValidElement(setter, parameter, nameIdentifier1);
                holder.registerProblem(nameIdentifier1, InspectionsBundle.message(
                  "inspection.nullable.problems.annotated.field.setter.parameter.conflict", StringUtil.getShortName(anno), notNullSimpleName),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       new AddAnnotationFix(anno, parameter, ArrayUtil.toStringArray(annoToRemove)));
              }
            }
          }

          for (PsiExpression rhs : findAllConstructorInitializers(field)) {
            if (rhs instanceof PsiReferenceExpression) {
              PsiElement target = ((PsiReferenceExpression)rhs).resolve();
              if (target instanceof PsiParameter) {
                PsiParameter parameter = (PsiParameter)target;
                if (REPORT_NOT_ANNOTATED_GETTER && !AnnotationUtil.isAnnotated(parameter, manager.getAllAnnotations(), false, false) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
                  final PsiIdentifier nameIdentifier2 = parameter.getNameIdentifier();
                  assert nameIdentifier2 != null : parameter;
                  holder.registerProblem(nameIdentifier2, InspectionsBundle
                    .message("inspection.nullable.problems.annotated.field.constructor.parameter.not.annotated",
                             StringUtil.getShortName(anno)),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new AddAnnotationFix(anno, parameter, ArrayUtil.toStringArray(annoToRemove)));
                  continue;
                }
                if (annotated.isDeclaredNotNull && manager.isNullable(parameter, false)) {
                  final PsiIdentifier nameIdentifier2 = parameter.getNameIdentifier();
                  assert nameIdentifier2 != null : parameter;
                  holder.registerProblem(nameIdentifier2, InspectionsBundle.message(
                    "inspection.nullable.problems.annotated.field.constructor.parameter.conflict", StringUtil.getShortName(anno),
                    nullableSimpleName),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                         new AddAnnotationFix(anno, parameter, ArrayUtil.toStringArray(annoToRemove)));
                }
                else if (annotated.isDeclaredNullable && manager.isNotNull(parameter, false)) {
                  boolean usedAsQualifier = !ReferencesSearch.search(parameter).forEach(new Processor<PsiReference>() {
                    @Override
                    public boolean process(PsiReference reference) {
                      final PsiElement element = reference.getElement();
                      if (element instanceof PsiReferenceExpression && element.getParent() instanceof PsiReferenceExpression) {
                        return false;
                      }
                      return true;
                    }
                  });
                  if (!usedAsQualifier) {
                    final PsiIdentifier nameIdentifier2 = parameter.getNameIdentifier();
                    assert nameIdentifier2 != null : parameter;
                    holder.registerProblem(nameIdentifier2, InspectionsBundle.message(
                      "inspection.nullable.problems.annotated.field.constructor.parameter.conflict", StringUtil.getShortName(anno),
                      notNullSimpleName),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           new AddAnnotationFix(anno, parameter, ArrayUtil.toStringArray(annoToRemove)));
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
    };
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
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(parameter.getProject());
    PsiAnnotation isDeclaredNotNull = AnnotationUtil.findAnnotation(parameter, manager.getNotNulls());
    PsiAnnotation isDeclaredNullable = AnnotationUtil.findAnnotation(parameter, manager.getNullables());
    if (isDeclaredNullable != null && isDeclaredNotNull != null) {
      reportNullableNotNullConflict(holder, parameter, isDeclaredNullable,  isDeclaredNotNull);
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

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.nullable.problems.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

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
          && NullableNotNullManager.isNotNull(superMethod)) {
        reported_nullable_method_overrides_notnull = true;
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      if (!reported_not_annotated_method_overrides_notnull
          && REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL
          && !annotated.isDeclaredNullable
          && !annotated.isDeclaredNotNull
          && NullableNotNullManager.isNotNull(superMethod)) {
        reported_not_annotated_method_overrides_notnull = true;
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final String[] annotationsToRemove = ArrayUtil.toStringArray(nullableManager.getNullables());
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.method.overrides.NotNull"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createAnnotateMethodFix(defaultNotNull, annotationsToRemove));
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
              nullableManager.isNotNull(parameter, false) &&
              nullableManager.isNullable(superParameter, false)) {
            reported_notnull_parameter_overrides_nullable[i] = true;
            holder.registerProblem(parameter.getNameIdentifier(),
                                   InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
          if (!reported_not_annotated_parameter_overrides_notnull[i] && REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL) {
            if (!AnnotationUtil.isAnnotated(parameter, nullableManager.getAllAnnotations(), false, false) &&
                nullableManager.isNotNull(superParameter, false)) {
              reported_not_annotated_parameter_overrides_notnull[i] = true;
              holder.registerProblem(parameter.getNameIdentifier(),
                                     InspectionsBundle.message("inspection.nullable.problems.parameter.overrides.NotNull"),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     new AddNotNullAnnotationFix(parameter));
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
        parameterAnnotated[i] = nullableManager.isNotNull(parameter, false);
        hasAnnotatedParameter |= parameterAnnotated[i];
      }
      if (hasAnnotatedParameter || annotated.isDeclaredNotNull) {
        PsiManager manager = method.getManager();
        PsiMethod[] overridings =
          OverridingMethodsSearch.search(method, GlobalSearchScope.allScope(manager.getProject()), true).toArray(PsiMethod.EMPTY_ARRAY);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (!manager.isInProject(overriding)) continue;
          if (!methodQuickFixSuggested
              && annotated.isDeclaredNotNull
              && !nullableManager.isNotNull(overriding, false) 
              && (nullableManager.isNullable(overriding, false) || !nullableManager.isNullable(overriding, true))) {
            method.getNameIdentifier(); //load tree
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, nullableManager.getNotNulls());
            final String defaultNotNull = nullableManager.getDefaultNotNull();
            final String[] annotationsToRemove = ArrayUtil.toStringArray(nullableManager.getNullables());
            holder.registerProblem(annotation, InspectionsBundle.message("nullable.stuff.problems.overridden.methods.are.not.annotated"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   new MyAnnotateMethodFix(defaultNotNull, annotationsToRemove));
            methodQuickFixSuggested = true;
          }
          if (hasAnnotatedParameter) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) continue;
              PsiParameter parameter = psiParameters[i];
              if (parameterAnnotated[i] && !nullableManager.isNotNull(parameter, false) && !nullableManager.isNullable(parameter, false)) {
                parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameters[i], nullableManager.getNotNulls());
                holder.registerProblem(annotation,
                                       InspectionsBundle.message("nullable.stuff.problems.overridden.method.parameters.are.not.annotated"),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       new AnnotateOverriddenMethodParameterFix(nullableManager.getDefaultNotNull(), nullableManager.getDefaultNullable()));
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  protected AnnotateMethodFix createAnnotateMethodFix(final String defaultNotNull, final String[] annotationsToRemove) {
    return new AnnotateMethodFix(defaultNotNull, annotationsToRemove);
  }

  private static void reportNullableNotNullConflict(final ProblemsHolder holder, final PsiModifierListOwner listOwner, final PsiAnnotation declaredNullable,
                                                    final PsiAnnotation declaredNotNull) {
    holder.registerProblem(declaredNotNull.isPhysical() ? declaredNotNull : listOwner.getNavigationElement(),
                           InspectionsBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(declaredNotNull, listOwner));
    holder.registerProblem(declaredNullable.isPhysical() ? declaredNullable : listOwner.getNavigationElement(),
                           InspectionsBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(declaredNullable, listOwner));
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private static class MyAddNullableAnnotationFix extends AddNullableAnnotationFix {
    public MyAddNullableAnnotationFix(PsiParameter parameter) {
      super(parameter);
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      return true;
    }
  }

  private static class MyAnnotateMethodFix extends AnnotateMethodFix {
    public MyAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
      super(defaultNotNull, annotationsToRemove);
    }

    protected boolean annotateOverriddenMethods() {
      return true;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("annotate.overridden.methods.as.notnull", ClassUtil.extractClassName(myAnnotation));
    }
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myNNParameterOverridesN;
    private JCheckBox myNAMethodOverridesNN;
    private JPanel myPanel;
    private JCheckBox myReportNotAnnotatedGetter;
    private JButton myConfigureAnnotationsButton;

    private OptionsPanel() {
      super(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      ActionListener actionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          apply();
        }
      };
      myNAMethodOverridesNN.addActionListener(actionListener);
      myNNParameterOverridesN.addActionListener(actionListener);
      myReportNotAnnotatedGetter.addActionListener(actionListener);
      myConfigureAnnotationsButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(OptionsPanel.this));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          final NullableNotNullDialog dialog = new NullableNotNullDialog(project);
          dialog.show();
        }
      });
      reset();
    }

    private void reset() {
      myNNParameterOverridesN.setSelected(REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE);
      myNAMethodOverridesNN.setSelected(REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
      myReportNotAnnotatedGetter.setSelected(REPORT_NOT_ANNOTATED_GETTER);
    }

    private void apply() {
      REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = myNAMethodOverridesNN.isSelected();
      REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = myNNParameterOverridesN.isSelected();
      REPORT_NOT_ANNOTATED_GETTER = myReportNotAnnotatedGetter.isSelected();
      REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL;
    }
  }

  public static List<PsiExpression> findAllConstructorInitializers(PsiField field) {
    final List<PsiExpression> result = ContainerUtil.createEmptyCOWList();
    ContainerUtil.addIfNotNull(result, field.getInitializer());

    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null) {
      LocalSearchScope scope = new LocalSearchScope(containingClass.getConstructors());
      ReferencesSearch.search(field, scope, false).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference reference) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression) {
            final PsiAssignmentExpression assignment = getAssignmentExpressionIfOnAssignmentLhs(element);
            final PsiMethod method = PsiTreeUtil.getParentOfType(assignment, PsiMethod.class);
            if (method != null && method.isConstructor() && assignment != null) {
              ContainerUtil.addIfNotNull(result, assignment.getRExpression());
            }
          }
          return true;
        }
      });
    }
    return result;
  }

  @Nullable
  private static PsiAssignmentExpression getAssignmentExpressionIfOnAssignmentLhs(PsiElement expression) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
    if (!(parent instanceof PsiAssignmentExpression)) {
      return null;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    if (!PsiTreeUtil.isAncestor(assignmentExpression.getLExpression(), expression, false)) {
      return null;
    }
    return assignmentExpression;
  }
}
