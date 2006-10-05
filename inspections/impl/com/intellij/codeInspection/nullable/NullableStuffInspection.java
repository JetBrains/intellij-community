package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.AnnotateQuickFix;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.AnnotateMethodFix;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class NullableStuffInspection extends BaseLocalInspectionTool {
  public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;

  @Nullable
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {

      }

      public void visitMethod(PsiMethod method) {
        checkNullableStuffForMethod(method, holder);
      }

      public void visitField(PsiField field) {
        boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(field, AnnotationUtil.NOT_NULL, false);
        boolean isDeclaredNullable = AnnotationUtil.isAnnotated(field, AnnotationUtil.NULLABLE, false);
        if (isDeclaredNullable && isDeclaredNotNull) {
          reportNullableNotNullConflict(holder, field.getNameIdentifier());
        }
        if ((isDeclaredNotNull || isDeclaredNullable) && TypeConversionUtil.isPrimitive(field.getType().getCanonicalText())) {
          reportPrimitiveType(holder, field.getNameIdentifier());
        }
      }

      public void visitParameter(PsiParameter parameter) {
        boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false);
        boolean isDeclaredNullable = AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NULLABLE, false);
        if (isDeclaredNullable && isDeclaredNotNull) {
          reportNullableNotNullConflict(holder, parameter.getNameIdentifier());
        }
        if ((isDeclaredNotNull || isDeclaredNullable) && TypeConversionUtil.isPrimitive(parameter.getType().getCanonicalText())) {
          reportPrimitiveType(holder, parameter.getNameIdentifier());
        }
      }
    };
  }

  private static void reportPrimitiveType(final ProblemsHolder holder, final PsiElement psiElement) {
    holder.registerProblem(psiElement,
                           InspectionsBundle.message("inspection.nullable.problems.primitive.type.annotation"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

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
    boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(method, AnnotationUtil.NOT_NULL, false);
    boolean isDeclaredNullable = AnnotationUtil.isAnnotated(method, AnnotationUtil.NULLABLE, false);
    if (isDeclaredNullable && isDeclaredNotNull) {
      reportNullableNotNullConflict(holder, method.getNameIdentifier());
    }
    PsiType returnType = method.getReturnType();
    if ((isDeclaredNotNull || isDeclaredNullable) && returnType != null && TypeConversionUtil.isPrimitive(returnType.getCanonicalText())) {
      reportPrimitiveType(holder, method.getReturnTypeElement());
    }

    PsiParameter[] parameters = method.getParameterList().getParameters();

    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);

    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL && isDeclaredNullable && AnnotationUtil.isNotNull(superMethod)) {
        holder.registerProblem(method.getNameIdentifier(),
                                               InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull"),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      if (REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !isDeclaredNullable && !isDeclaredNotNull && AnnotationUtil.isNotNull(superMethod)) {
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.method.overrides.NotNull"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new AnnotateMethodFix(AnnotationUtil.NOT_NULL){
                                 protected int askUserWhetherToAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
                                   return NullableStuffInspection.this.askUserWhetherToAnnotateBaseMethod(method, superMethod, project);
                                 }
                               });
      }
      if (REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE || REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL) {
        PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
        if (superParameters.length != parameters.length) {
          continue;
        }
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiParameter superParameter = superParameters[i];
          if (REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE
              && AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false)
              && AnnotationUtil.isAnnotated(superParameter, AnnotationUtil.NULLABLE, false)) {
            holder.registerProblem(parameter.getNameIdentifier(),
                                   InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
          if (REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL
              && !AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false)
              && !AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NULLABLE, false)
              && AnnotationUtil.isAnnotated(superParameter, AnnotationUtil.NOT_NULL, false)) {
            holder.registerProblem(parameter.getNameIdentifier(),
                                   InspectionsBundle.message("inspection.nullable.problems.parameter.overrides.NotNull"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   new AnnotateQuickFix(parameter, AnnotationUtil.NOT_NULL));
          }
        }
      }
    }
  }

  protected int askUserWhetherToAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
    return new AnnotateMethodFix(AnnotationUtil.NOT_NULL){
      public int askUserWhetherToAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
        return super.askUserWhetherToAnnotateBaseMethod(method, superMethod, project);
      }
    }.askUserWhetherToAnnotateBaseMethod(method, superMethod, project);
  }

  private static void reportNullableNotNullConflict(final ProblemsHolder holder, PsiIdentifier psiElement) {
    holder.registerProblem(psiElement,
                           InspectionsBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myNNParameterOverridesN;
    private JCheckBox myNAMethodOverridesNN;
    private JCheckBox myNMethodOverridesNN;
    private JCheckBox myNAParameterOverridesNN;
    private JPanel myPanel;

    private OptionsPanel() {
      super(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      ActionListener actionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          apply();
        }
      };
      myNAMethodOverridesNN.addActionListener(actionListener);
      myNMethodOverridesNN.addActionListener(actionListener);
      myNNParameterOverridesN.addActionListener(actionListener);
      myNAParameterOverridesNN.addActionListener(actionListener);
      reset();
    }

    private void reset() {
      myNNParameterOverridesN.setSelected(REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE);
      myNAMethodOverridesNN.setSelected(REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
      myNMethodOverridesNN.setSelected(REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL);
      myNAParameterOverridesNN.setSelected(REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL);
    }

    private void apply() {
      REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = myNAMethodOverridesNN.isSelected();
      REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = myNNParameterOverridesN.isSelected();
      REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = myNMethodOverridesNN.isSelected();
      REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = myNAParameterOverridesNN.isSelected();
    }
  }
}
