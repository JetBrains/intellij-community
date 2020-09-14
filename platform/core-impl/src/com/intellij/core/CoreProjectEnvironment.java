// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.mock.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbUtil;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactoryImpl;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

public class CoreProjectEnvironment {
  private final Disposable myParentDisposable;
  private final CoreApplicationEnvironment myEnvironment;

  protected final FileIndexFacade myFileIndexFacade;
  protected final PsiManagerImpl myPsiManager;
  protected final MockProject myProject;
  protected final MessageBus myMessageBus;

  public CoreProjectEnvironment(@NotNull Disposable parentDisposable, @NotNull CoreApplicationEnvironment applicationEnvironment) {
    myParentDisposable = parentDisposable;
    myEnvironment = applicationEnvironment;
    myProject = createProject(myEnvironment.getApplication().getPicoContainer(), myParentDisposable);

    preregisterServices();

    myFileIndexFacade = createFileIndexFacade();
    myMessageBus = myProject.getMessageBus();

    PsiModificationTrackerImpl modificationTracker = new PsiModificationTrackerImpl(myProject);
    myProject.registerService(PsiModificationTracker.class, modificationTracker);
    myProject.registerService(FileIndexFacade.class, myFileIndexFacade);
    myProject.registerService(ResolveCache.class, new ResolveCache(myProject));

    myPsiManager = new PsiManagerImpl(myProject);
    myProject.registerService(PsiManager.class, myPsiManager);
    myProject.registerService(SmartPointerManager.class, SmartPointerManagerImpl.class);
    myProject.registerService(DocumentCommitProcessor.class, new MockDocumentCommitProcessor());
    myProject.registerService(PsiDocumentManager.class, new CorePsiDocumentManager(myProject));

    myProject.registerService(ResolveScopeManager.class, createResolveScopeManager(myPsiManager));

    myProject.registerService(PsiFileFactory.class, new PsiFileFactoryImpl(myPsiManager));
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myProject)));
    myProject.registerService(PsiDirectoryFactory.class, new PsiDirectoryFactoryImpl(myProject));
    myProject.registerService(ProjectScopeBuilder.class, createProjectScopeBuilder());
    myProject.registerService(DumbService.class, new MockDumbService(myProject));
    myProject.registerService(DumbUtil.class, new MockDumbUtil());
    myProject.registerService(CoreEncodingProjectManager.class, CoreEncodingProjectManager.class);
    myProject.registerService(InjectedLanguageManager.class, new CoreInjectedLanguageManager());
  }

  @NotNull
  protected MockProject createProject(@NotNull PicoContainer parent, @NotNull Disposable parentDisposable) {
    return new MockProject(parent, parentDisposable);
  }

  @NotNull
  protected ProjectScopeBuilder createProjectScopeBuilder() {
    return new CoreProjectScopeBuilder(myProject, myFileIndexFacade);
  }

  protected void preregisterServices() {

  }

  @NotNull
  protected FileIndexFacade createFileIndexFacade() {
    return new MockFileIndexFacade(myProject);
  }

  @NotNull
  protected ResolveScopeManager createResolveScopeManager(@NotNull PsiManager psiManager) {
    return new MockResolveScopeManager(psiManager.getProject());
  }

  public <T> void registerProjectExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName,
                                                @NotNull Class<? extends T> aClass) {
    CoreApplicationEnvironment.registerExtensionPoint(myProject.getExtensionArea(), extensionPointName, aClass);
  }

  public <T> void addProjectExtension(@NotNull ExtensionPointName<T> name, @NotNull final T extension) {
    //noinspection TestOnlyProblems
    name.getPoint(myProject).registerExtension(extension, myParentDisposable);
  }

  public <T> void registerProjectComponent(@NotNull Class<T> interfaceClass, @NotNull T implementation) {
    CoreApplicationEnvironment.registerComponentInstance(myProject.getPicoContainer(), interfaceClass, implementation);
    if (implementation instanceof Disposable) {
      Disposer.register(myProject, (Disposable) implementation);
    }
  }

  @NotNull
  public Disposable getParentDisposable() {
    return myParentDisposable;
  }

  @NotNull
  public CoreApplicationEnvironment getEnvironment() {
    return myEnvironment;
  }

  @NotNull
  public MockProject getProject() {
    return myProject;
  }
}
