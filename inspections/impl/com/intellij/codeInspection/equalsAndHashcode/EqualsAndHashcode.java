package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class EqualsAndHashcode extends DescriptorProviderInspection {
  private static final JobDescriptor JOB_DESCRIPTOR = new JobDescriptor(InspectionsBundle.message("inspection.equals.hashcode.job.descriptor"));

  private PsiMethod myHashCode;
  private PsiMethod myEquals;
  private static final Logger LOG = Logger.getInstance(EqualsAndHashcode.class.getName());

  public void initialize(GlobalInspectionContextImpl context) {
    super.initialize(context);
    myHashCode = null;
    myEquals = null;
    PsiManager psiManager = PsiManager.getInstance(getContext().getProject());
    PsiClass psiObjectClass = psiManager.findClass("java.lang.Object");
    LOG.assertTrue(psiObjectClass != null);
    PsiMethod[] methods = psiObjectClass.getMethods();
    for (PsiMethod method : methods) {
      @NonNls final String name = method.getName();
      if ("equals".equals(name)) {
          myEquals = method;
      } else if ("hashCode".equals(name)) {
          myHashCode = method;
      }
    }
  }

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    JOB_DESCRIPTOR.setTotalAmount(scope.getFileCount());

    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitFile(PsiFile file) {
        if (file instanceof PsiJavaFile) {
          final VirtualFile virtualFile = file.getVirtualFile();
          LOG.assertTrue(virtualFile != null);
          getContext().incrementJobDoneAmount(JOB_DESCRIPTOR, virtualFile.getPresentableUrl());
          super.visitFile(file);
        }
      }

      public void visitClass(PsiClass aClass) {
        if (!getContext().isToCheckMember(aClass, EqualsAndHashcode.this)) return;
        super.visitClass(aClass);

        boolean hasEquals = false;
        boolean hasHashCode = false;
        PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
           if (MethodSignatureUtil.areSignaturesEqual(method, myEquals)) {
              hasEquals = true;
           } else if (MethodSignatureUtil.areSignaturesEqual(method, myHashCode)) {
              hasHashCode = true;
           }
        }

          if (hasEquals != hasHashCode) {
            PsiIdentifier identifier = aClass.getNameIdentifier();
            addProblemElement(getContext().getRefManager().getReference(aClass),
                            new ProblemDescriptor[]{manager.createProblemDescriptor(identifier != null ? identifier : aClass,
                                                                                    hasEquals
                                                                                    ? InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>equals()</code>", "<code>hashCode()</code>")
                                                                                    : InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>hashCode()</code>", "<code>equals()</code>"),
                                                                                    (LocalQuickFix [])null,
                                                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING)});
        }
      }
    });
  }

  @NotNull
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
