/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.core;

import com.intellij.mock.MockDumbService;
import com.intellij.mock.MockFileIndexFacade;
import com.intellij.mock.MockProject;
import com.intellij.mock.MockResolveScopeManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactoryImpl;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
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
    myProject.registerService(ResolveCache.class, new ResolveCache(myMessageBus));

    myPsiManager = new PsiManagerImpl(myProject, null, null, myFileIndexFacade, myMessageBus, modificationTracker);
    ((FileManagerImpl) myPsiManager.getFileManager()).markInitialized();
    registerProjectComponent(PsiManager.class, myPsiManager);
    myProject.registerService(SmartPointerManager.class, SmartPointerManagerImpl.class);
    registerProjectComponent(PsiDocumentManager.class, new CorePsiDocumentManager(myProject, myPsiManager,
                                                                                  myMessageBus,
                                                                                  new MockDocumentCommitProcessor()));

    myProject.registerService(ResolveScopeManager.class, createResolveScopeManager(myPsiManager));

    myProject.registerService(PsiFileFactory.class, new PsiFileFactoryImpl(myPsiManager));
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myPsiManager)));
    myProject.registerService(PsiDirectoryFactory.class, new PsiDirectoryFactoryImpl(myPsiManager));
    myProject.registerService(ProjectScopeBuilder.class, createProjectScopeBuilder());
    myProject.registerService(DumbService.class, new MockDumbService(myProject));
    myProject.registerService(CoreEncodingProjectManager.class, CoreEncodingProjectManager.class);
  }

  @SuppressWarnings("MethodMayBeStatic")
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

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  protected ResolveScopeManager createResolveScopeManager(@NotNull PsiManager psiManager) {
    return new MockResolveScopeManager(psiManager.getProject());
  }

  public <T> void registerProjectExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName,
                                                @NotNull Class<? extends T> aClass) {
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getArea(myProject), extensionPointName, aClass);
  }

  public <T> void addProjectExtension(@NotNull ExtensionPointName<T> name, @NotNull final T extension) {
    final ExtensionPoint<T> extensionPoint = Extensions.getArea(myProject).getExtensionPoint(name);
    extensionPoint.registerExtension(extension);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }


  public <T> void registerProjectComponent(@NotNull Class<T> interfaceClass, @NotNull T implementation) {
    CoreApplicationEnvironment.registerComponentInstance(myProject.getPicoContainer(), interfaceClass, implementation);
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
