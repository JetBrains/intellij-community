package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class EqualsAndHashcode extends LocalInspectionTool {

  private PsiMethod myHashCode;
  private PsiMethod myEquals;
  private static final Logger LOG = Logger.getInstance(EqualsAndHashcode.class.getName());

  public void projectOpened(Project project) {
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiClass psiObjectClass = psiManager.findClass("java.lang.Object");
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

  @Nullable
  public PsiElementVisitor buildVisitor(final ProblemsHolder holder, boolean isOnTheFly) {
    if (myEquals == null || myHashCode == null) return null;  //jdk wasn't configured for the project
    if (!myEquals.isValid() || !myHashCode.isValid()) return null;
    return new PsiElementVisitor() {
      public void visitClass(PsiClass aClass) {
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

      public void visitReferenceExpression(PsiReferenceExpression expression) {
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

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.equals.hashcode.display.name");
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return "EqualsAndHashcode";
  }

  public void projectClosed(Project project) {
    myEquals = null;
    myHashCode = null;
  }
}
