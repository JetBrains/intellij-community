// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.lang.jvm.facade.JvmFacade;
import com.intellij.lang.jvm.facade.JvmFacadeImpl;
import com.intellij.mock.MockFileIndexFacade;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JvmPsiConversionHelper;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.JvmPsiConversionHelperImpl;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Used in Kotlin.
 */
public class JavaCoreProjectEnvironment extends CoreProjectEnvironment {
  private final JavaFileManager myFileManager;
  private final PackageIndex myPackageIndex;

  public JavaCoreProjectEnvironment(@NotNull Disposable parentDisposable, @NotNull CoreApplicationEnvironment applicationEnvironment) {
    super(parentDisposable, applicationEnvironment);

    project.registerService(PsiElementFactory.class, new PsiElementFactoryImpl(project));
    project.registerService(JavaPsiImplementationHelper.class, createJavaPsiImplementationHelper());
    project.registerService(PsiResolveHelper.class, new PsiResolveHelperImpl(project));
    project.registerService(LanguageLevelProjectExtension.class, new CoreLanguageLevelProjectExtension());
    project.registerService(JavaResolveCache.class, new JavaResolveCache(project));
    project.registerService(JavaCodeStyleSettingsFacade.class, new CoreJavaCodeStyleSettingsFacade());
    project.registerService(JavaFileCodeStyleFacade.class, new CoreJavaFileCodeStyleFacade());
    project.registerService(JavaCodeStyleManager.class, new CoreJavaCodeStyleManager());
    project.registerService(ControlFlowFactory.class, new ControlFlowFactory(project));

    myPackageIndex = createCorePackageIndex();
    project.registerService(PackageIndex.class, myPackageIndex);

    myFileManager = createCoreFileManager();
    project.registerService(JavaFileManager.class, myFileManager);

    project.registerService(JvmPsiConversionHelper.class, new JvmPsiConversionHelperImpl());
    registerJavaPsiFacade();
    project.registerService(JvmFacade.class, new JvmFacadeImpl(project));
  }

  protected void registerJavaPsiFacade() {
    JavaPsiFacadeImpl javaPsiFacade = new JavaPsiFacadeImpl(project);
    project.registerService(JavaPsiFacade.class, javaPsiFacade);
  }

  protected CoreJavaPsiImplementationHelper createJavaPsiImplementationHelper() {
    return new CoreJavaPsiImplementationHelper(project);
  }

  protected JavaFileManager createCoreFileManager() {
    return new CoreJavaFileManager(myPsiManager);
  }

  protected PackageIndex createCorePackageIndex() {
    return new CorePackageIndex();
  }

  public void addJarToClassPath(File path) {
    assert path.isFile();

    final VirtualFile root = getEnvironment().getJarFileSystem().findFileByPath(path + "!/");
    if (root == null) {
      throw new IllegalArgumentException("trying to add non-existing file to classpath: " + path);
    }

    addSourcesToClasspath(root);
  }

  public void addSourcesToClasspath(@NotNull VirtualFile root) {
    assert root.isDirectory();
    ((CoreJavaFileManager)myFileManager).addToClasspath(root);
    ((CorePackageIndex)myPackageIndex).addToClasspath(root);
    ((MockFileIndexFacade)myFileIndexFacade).addLibraryRoot(root);
  }
}