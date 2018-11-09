// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
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

  public JavaCoreProjectEnvironment(Disposable parentDisposable, CoreApplicationEnvironment applicationEnvironment) {
    super(parentDisposable, applicationEnvironment);

    myProject.registerService(PsiElementFactory.class, new PsiElementFactoryImpl(myPsiManager));
    myProject.registerService(JavaPsiImplementationHelper.class, createJavaPsiImplementationHelper());
    myProject.registerService(PsiResolveHelper.class, new PsiResolveHelperImpl(myPsiManager));
    myProject.registerService(LanguageLevelProjectExtension.class, new CoreLanguageLevelProjectExtension());
    myProject.registerService(JavaResolveCache.class, new JavaResolveCache(myMessageBus));
    myProject.registerService(JavaCodeStyleSettingsFacade.class, new CoreJavaCodeStyleSettingsFacade());
    myProject.registerService(JavaCodeStyleManager.class, new CoreJavaCodeStyleManager());
    myProject.registerService(ControlFlowFactory.class, new ControlFlowFactory(myPsiManager));

    myPackageIndex = createCorePackageIndex();
    myProject.registerService(PackageIndex.class, myPackageIndex);

    myFileManager = createCoreFileManager();
    myProject.registerService(JavaFileManager.class, myFileManager);

    myProject.registerService(JvmPsiConversionHelper.class, new JvmPsiConversionHelperImpl());
    registerJavaPsiFacade();
    myProject.registerService(JvmFacade.class, new JvmFacadeImpl(myProject, myMessageBus));
  }

  protected void registerJavaPsiFacade() {
    JavaPsiFacadeImpl javaPsiFacade = new JavaPsiFacadeImpl(myProject, myPsiManager, myFileManager, myMessageBus);
    myProject.registerService(JavaPsiFacade.class, javaPsiFacade);
  }

  protected CoreJavaPsiImplementationHelper createJavaPsiImplementationHelper() {
    return new CoreJavaPsiImplementationHelper(myProject);
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