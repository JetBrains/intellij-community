package com.intellij.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NonNls;
import org.objectweb.asm.ClassWriter;

/**
 * @author yole
 */
public class PsiClassWriter extends ClassWriter {
  private final Project myProject;

  public PsiClassWriter(final Project project, boolean isJava6) {
    super(isJava6 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS);
    myProject = project;
  }

  public PsiClassWriter(final Module module) {
    this(module.getProject(), isJdk6(module));
  }

  private static boolean isJdk6(final Module module) {
    final ProjectJdk projectJdk = ModuleRootManager.getInstance(module).getJdk();
    if (projectJdk == null) return false;
    String versionString = projectJdk.getVersionString();
    return versionString.contains("1.6") || versionString.contains("6.0");
  }

  protected String getCommonSuperClass(final String type1, final String type2) {
    //PsiManager.getInstance(myProject).findClass(type1.replace('/', '.').replace('$', '.'), myProject.getAllScope());
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @NonNls
      public String compute() {
        final PsiManager manager = PsiManager.getInstance(myProject);
        PsiClass c = ClassUtil.findPsiClassByJVMName(manager, type1);
        if (c == null) {
          return "java/lang/Object";
        }
        PsiClass d = ClassUtil.findPsiClassByJVMName(manager, type2);
        if (d == null) {
          return "java/lang/Object";
        }
        if (c.isInheritor(d, true)) {
          return ClassUtil.getJVMClassName(d).replace('.', '/');
        }
        if (d.isInheritor(c, true)) {
          return ClassUtil.getJVMClassName(c).replace('.', '/');
        }
        if (c.isInterface() || d.isInterface()) {
          return "java/lang/Object";
        }
        do {
          c = c.getSuperClass();
        }
        while (c != null && !d.isInheritor(c, true));
        if (c == null) {
          return "java/lang/Object";
        }
        return ClassUtil.getJVMClassName(c).replace('.', '/');
      }
    });
  }

}