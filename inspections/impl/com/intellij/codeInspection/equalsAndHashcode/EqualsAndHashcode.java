package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class EqualsAndHashcode extends BaseJavaLocalInspectionTool {

  private PsiMethod myHashCode;
  private PsiMethod myEquals;

  public void projectOpened(Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiClass psiObjectClass = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiClass>() {
          @Nullable
          public PsiClass compute() {
            return JavaPsiFacade.getInstance(psiManager.getProject()).findClass("java.lang.Object");
          }
        }
    );
    if (psiObjectClass != null) {
      PsiMethod[] methods = psiObjectClass.getMethods();
      for (PsiMethod method : methods) {
        @NonNls final String name = method.getName();
        if ("equals".equals(name)) {
          myEquals = method;
        }
        else if ("hashCode".equals(name)) {
          myHashCode = method;
        }
      }
    }
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    //jdk wasn't configured for the project
    if (myEquals == null || myHashCode == null || !myEquals.isValid() || !myHashCode.isValid()) return new PsiElementVisitor() {};

    return new JavaElementVisitor() {
      @Override public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        boolean [] hasEquals = new boolean[] {false};
        boolean [] hasHashCode = new boolean[] {false};
        processClass(aClass, hasEquals, hasHashCode);
        if (hasEquals[0] != hasHashCode[0]) {
          PsiIdentifier identifier = aClass.getNameIdentifier();
          holder.registerProblem(identifier != null ? identifier : aClass,
                                 hasEquals[0]
                                  ? InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>equals()</code>", "<code>hashCode()</code>")
                                  : InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor","<code>hashCode()</code>", "<code>equals()</code>"),
                                 (LocalQuickFix[])null);
        }
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        //do nothing
      }
    };
  }

  private void processClass(final PsiClass aClass, final boolean[] hasEquals, final boolean[] hasHashCode) {
    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (MethodSignatureUtil.areSignaturesEqual(method, myEquals)) {
        hasEquals[0] = true;
      }
      else if (MethodSignatureUtil.areSignaturesEqual(method, myHashCode)) {
        hasHashCode[0] = true;
      }
    }
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.equals.hashcode.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return "EqualsAndHashcode";
  }

  public void projectClosed(Project project) {
    myEquals = null;
    myHashCode = null;
  }
}
