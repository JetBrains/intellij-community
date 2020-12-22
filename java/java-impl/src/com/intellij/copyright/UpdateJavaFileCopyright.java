// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.JavaOptions;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UpdateJavaFileCopyright extends UpdatePsiFileCopyright {
  private static final Logger LOG = Logger.getInstance(UpdateJavaFileCopyright.class.getName());

  public UpdateJavaFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options) {
    super(project, module, root, options);
  }

  @Override
  protected boolean accept() {
    return getFile() instanceof PsiJavaFile;
  }

  @Override
  protected void scanFile() {
    LOG.debug("updating " + getFile().getVirtualFile());

    PsiClassOwner javaFile = (PsiClassOwner)getFile();
    PsiElement pkg = getPackageStatement();
    PsiElement[] imports = getImportsList();
    PsiClass topClass = null;
    PsiClass[] classes = javaFile.getClasses();
    if (classes.length > 0) {
      topClass = classes[0];
    }

    PsiElement first = javaFile.getFirstChild();

    int location = getLanguageOptions().getFileLocation();
    if (pkg != null) {
      checkComments(first, pkg, location == JavaOptions.LOCATION_BEFORE_PACKAGE);
      first = pkg;
    }
    else if (location == JavaOptions.LOCATION_BEFORE_PACKAGE) {
      location = JavaOptions.LOCATION_BEFORE_IMPORT;
    }

    if (imports != null && imports.length > 0) {
      checkComments(first, imports[0], location == JavaOptions.LOCATION_BEFORE_IMPORT);
      first = imports[0];
    }
    else if (location == JavaOptions.LOCATION_BEFORE_IMPORT) {
      location = JavaOptions.LOCATION_BEFORE_CLASS;
    }

    if (topClass != null) {
      final List<PsiComment> comments = new ArrayList<>();
      collectComments(first, topClass, comments);
      collectComments(topClass.getFirstChild(), topClass.getModifierList(), comments);
      checkCommentsForTopClass(topClass, location, comments);
    }
    else if (location == JavaOptions.LOCATION_BEFORE_CLASS) {
      // no package, no imports, no top level class
    }
  }

  protected void checkCommentsForTopClass(PsiClass topClass, int location, List<PsiComment> comments) {
    checkComments(topClass.getModifierList(), location == JavaOptions.LOCATION_BEFORE_CLASS, comments);
  }

  protected PsiElement @Nullable [] getImportsList() {
    final PsiJavaFile javaFile = (PsiJavaFile)getFile();
    assert javaFile != null;
    final PsiImportList importList = javaFile.getImportList();
    return importList == null ? null : importList.getChildren();
  }

  @Nullable
  protected PsiElement getPackageStatement() {
    PsiJavaFile javaFile = (PsiJavaFile)getFile();
    assert javaFile != null;
    return javaFile.getPackageStatement();
  }

  static final class UpdateJavaCopyrightsProvider extends UpdateCopyrightsProvider {
    @Override
    public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
      return new UpdateJavaFileCopyright(project, module, file, options);
    }
  }
}