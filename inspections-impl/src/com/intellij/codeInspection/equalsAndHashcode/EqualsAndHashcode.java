package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class EqualsAndHashcode extends DescriptorProviderInspection {
  private static final JobDescriptor JOB_DESCRIPTOR = new JobDescriptor(InspectionsBundle.message("inspection.equals.hashcode.job.descriptor"));

  private PsiMethod myHashCode;
  private PsiMethod myEquals;

  public EqualsAndHashcode() {
  }

  public void initialize(InspectionManagerEx manager) {
    super.initialize(manager);
    myHashCode = null;
    myEquals = null;
    PsiManager psiManager = PsiManager.getInstance(getManager().getProject());
    PsiClass psiObjectClass = psiManager.findClass("java.lang.Object");
    PsiMethod[] methods = psiObjectClass.getMethods();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      @NonNls final String name = method.getName();
      if ("equals".equals(name)) {
        myEquals = method;
      } else
        if ("hashCode".equals(name)) {
          myHashCode = method;
        }
    }
  }

  public void runInspection(AnalysisScope scope) {
    JOB_DESCRIPTOR.setTotalAmount(scope.getFileCount());

    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitFile(PsiFile file) {
        if (file instanceof PsiJavaFile) {
          getManager().incrementJobDoneAmount(JOB_DESCRIPTOR, file.getVirtualFile().getPresentableUrl());
          super.visitFile(file);
        }
      }

      public void visitClass(PsiClass aClass) {
        if (!InspectionManagerEx.isToCheckMember(aClass, EqualsAndHashcode.this.getShortName())) return;
        super.visitClass(aClass);

        boolean hasEquals = false;
        boolean hasHashCode = false;
        PsiMethod[] methods = aClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
          PsiMethod method = methods[i];
          if (MethodSignatureUtil.areSignaturesEqual(method,  myEquals)) {
            hasEquals = true;
          } else if (MethodSignatureUtil.areSignaturesEqual(method, myHashCode)) {
            hasHashCode = true;
          }
        }

        if (hasEquals != hasHashCode) {
          addProblemElement(getManager().getRefManager().getReference(aClass),
                            new ProblemDescriptor[]{getManager().createProblemDescriptor(aClass,
                                                                                         hasEquals
                                                                                         ? InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>equals()</code>", "<code>hashCode()</code>")
                                                                                         : InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>hashCode()</code>", "<code>equals()</code>"),
                                                                                         (LocalQuickFix [])null,
                                                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING)});
        }
      }
    });
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {JOB_DESCRIPTOR};
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.equals.hashcode.display.name");
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return "EqualsAndHashcode";
  }
}
