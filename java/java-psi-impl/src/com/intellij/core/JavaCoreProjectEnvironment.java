// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.lang.jvm.facade.JvmElementProvider;
import com.intellij.lang.jvm.facade.JvmFacade;
import com.intellij.lang.jvm.facade.JvmFacadeImpl;
import com.intellij.mock.MockFileIndexFacade;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JvmPsiConversionHelper;
import com.intellij.psi.PackageDirectoryProvider;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.SmartTypePointerManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.JvmPsiConversionHelperImpl;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.impl.file.JavaPackageDirectoryProvider;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Used in Kotlin.
 */
@Internal
public class JavaCoreProjectEnvironment extends CoreProjectEnvironment {
  private final JavaFileManager myFileManager;
  private final PackageIndex myPackageIndex;

  public JavaCoreProjectEnvironment(@NotNull Disposable parentDisposable, @NotNull CoreApplicationEnvironment applicationEnvironment) {
    super(parentDisposable, applicationEnvironment);

    ExtensionsArea area = project.getExtensionArea();
    CoreApplicationEnvironment.registerExtensionPoint(area, PsiElementFinder.EP.getName(), PsiElementFinder.class);
    CoreApplicationEnvironment.registerExtensionPoint(area, JvmElementProvider.EP_NAME, JvmElementProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(area, PackageDirectoryProvider.EP_NAME.getName(), PackageDirectoryProvider.class);

    project.registerService(ExternalAnnotationsManager.class, createExternalAnnotationsManager());
    project.registerService(InferredAnnotationsManager.class, createInferredAnnotationsManager());
    project.registerService(PsiNameHelper.class, PsiNameHelperImpl.class);
    project.registerService(PsiJavaModuleModificationTracker.class, PsiJavaModuleModificationTracker.class);
    project.registerService(SmartTypePointerManager.class, SmartTypePointerManagerImpl.class);
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
    // the finder resolves a class or a package by its qualified name through the Java file manager; a client adds its own finders
    // through the extension point
    addProjectExtension(new ExtensionPointName<>(PsiElementFinder.EP.getName()), new PsiElementFinderImpl(project));
    addProjectExtension(new ExtensionPointName<>(PackageDirectoryProvider.EP_NAME.getName()), new JavaPackageDirectoryProvider());
    project.registerService(JvmFacade.class, new JvmFacadeImpl(project));
  }

  protected void registerJavaPsiFacade() {
    JavaPsiFacadeImpl javaPsiFacade = new JavaPsiFacadeImpl(project, null);
    project.registerService(JavaPsiFacade.class, javaPsiFacade);
  }

  protected @NotNull ExternalAnnotationsManager createExternalAnnotationsManager() {
    return new CoreExternalAnnotationsManager();
  }

  protected @NotNull InferredAnnotationsManager createInferredAnnotationsManager() {
    return new CoreInferredAnnotationsManager();
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

  @ApiStatus.Obsolete
  public void addJarToClassPath(@SuppressWarnings("IO_FILE_USAGE") File path) {
    addJarToClasspath(path.toPath());
  }

  public void addJarToClasspath(@NotNull Path path) {
    assert Files.isRegularFile(path);

    final VirtualFile root = getEnvironment().getJarFileSystem().findFileByPath(path.toAbsolutePath() + "!/");
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