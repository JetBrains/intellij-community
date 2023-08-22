// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.org.objectweb.asm.ClassWriter;


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
    final Sdk projectJdk = ModuleRootManager.getInstance(module).getSdk();
    if (projectJdk == null) return false;
    return JavaSdk.getInstance().isOfVersionOrHigher(projectJdk, JavaSdkVersion.JDK_1_6);
  }

  @Override
  protected String getCommonSuperClass(final String type1, final String type2) {
    //PsiManager.getInstance(myProject).findClass(type1.replace('/', '.').replace('$', '.'), myProject.getAllScope());
    return ReadAction.compute(() -> {
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
    });
  }

}