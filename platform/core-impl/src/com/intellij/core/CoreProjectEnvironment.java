// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl;
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
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

public class CoreProjectEnvironment {
  private final Disposable myParentDisposable;
  private final CoreApplicationEnvironment myEnvironment;

  protected final FileIndexFacade myFileIndexFacade;
  protected final PsiManagerImpl myPsiManager;
  protected final MockProject project;

  public CoreProjectEnvironment(@NotNull Disposable parentDisposable, @NotNull CoreApplicationEnvironment applicationEnvironment) {
    myParentDisposable = parentDisposable;
    myEnvironment = applicationEnvironment;
    project = createProject(myEnvironment.getApplication().getPicoContainer(), myParentDisposable);

    preregisterServices();

    myFileIndexFacade = createFileIndexFacade();

    PsiModificationTrackerImpl modificationTracker = new PsiModificationTrackerImpl(project);
    project.registerService(PsiModificationTracker.class, modificationTracker);
    project.registerService(FileIndexFacade.class, myFileIndexFacade);
    project.registerService(ResolveCache.class, new ResolveCache(project));

    project.registerService(CodeInsightContextManager.class, new CodeInsightContextManagerImpl(project, project.getCoroutineScope()));

    myPsiManager = new PsiManagerImpl(project);
    project.registerService(PsiManager.class, myPsiManager);
    project.registerService(SmartPointerManager.class, SmartPointerManagerImpl.class);
    project.registerService(DocumentCommitProcessor.class, new MockDocumentCommitProcessor());
    project.registerService(PsiDocumentManager.class, new CorePsiDocumentManager(project));

    project.registerService(ResolveScopeManager.class, createResolveScopeManager(myPsiManager));

    project.registerService(PsiFileFactory.class, new PsiFileFactoryImpl(myPsiManager));
    project.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(project, new PsiCachedValuesFactory(project)));
    project.registerService(PsiDirectoryFactory.class, new PsiDirectoryFactoryImpl(project));
    project.registerService(ProjectScopeBuilder.class, createProjectScopeBuilder());
    project.registerService(DumbService.class, new MockDumbService(project));
    project.registerService(DumbUtil.class, new MockDumbUtil());
    project.registerService(CoreEncodingProjectManager.class, CoreEncodingProjectManager.class);
    project.registerService(InjectedLanguageManager.class, new CoreInjectedLanguageManager());
  }

  @Internal
  protected @NotNull MockProject createProject(@NotNull PicoContainer parent, @NotNull Disposable parentDisposable) {
    return new MockProject(parent, parentDisposable);
  }

  protected @NotNull ProjectScopeBuilder createProjectScopeBuilder() {
    return new CoreProjectScopeBuilder(project, myFileIndexFacade);
  }

  protected void preregisterServices() {

  }

  protected @NotNull FileIndexFacade createFileIndexFacade() {
    return new MockFileIndexFacade(project);
  }

  protected @NotNull ResolveScopeManager createResolveScopeManager(@NotNull PsiManager psiManager) {
    return new MockResolveScopeManager(psiManager.getProject());
  }

  public <T> void addProjectExtension(@NotNull ExtensionPointName<T> name, final @NotNull T extension) {
    //noinspection TestOnlyProblems
    name.getPoint(project).registerExtension(extension, myParentDisposable);
  }

  public <T> void registerProjectComponent(@NotNull Class<T> interfaceClass, @NotNull T implementation) {
    CoreApplicationEnvironment.registerComponentInstance(project.getPicoContainer(), interfaceClass, implementation);
    if (implementation instanceof Disposable) {
      Disposer.register(project, (Disposable) implementation);
    }
  }

  public @NotNull Disposable getParentDisposable() {
    return myParentDisposable;
  }

  public @NotNull CoreApplicationEnvironment getEnvironment() {
    return myEnvironment;
  }

  public @NotNull MockProject getProject() {
    return project;
  }
}
