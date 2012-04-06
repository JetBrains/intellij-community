/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.core.indexing.*;
import com.intellij.lang.cacheBuilder.CacheBuilderEP;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystemWithId;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceServiceImpl;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.impl.search.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilService;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.psi.stubs.SerializationManagerImpl;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.QueryExecutor;
import com.intellij.util.indexing.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class JavaCoreEnvironmentWithIndex extends JavaCoreEnvironment {
  private static final Logger LOG = Logger.getInstance("#com.intellij.core.JavaCoreEnvironmentWithIndex");

  public JavaCoreEnvironmentWithIndex(Disposable parentDisposable) {
    super(parentDisposable);
    myApplication.registerService(ProgressManager.class, ProgressManagerJavaComponent.class);

    myApplication.registerService(ProjectHolder.class, new ProjectHolder(myProject));

    myProject.registerService(InjectedLanguageUtilService.class, InjectedLanguageUtilServiceJavaComponent.class);
    myProject.registerService(PsiSearchHelper.class, PsiSearchHelperJavaComponent.class);
    myProject.registerService(FileBasedIndexProjectHandlerJavaComponent.class, FileBasedIndexProjectHandlerJavaComponent.class);

    myApplication.registerService(SerializationManager.class, SerializationManagerImpl.class);
    myApplication.registerService(AbstractVfsAdapter.class, new AbstractVfsAdapterJavaComponent((CoreLocalFileSystemWithId)getLocalFileSystem()));
    myApplication.registerService(FileBasedIndex.class, FileBasedIndexJavaComponent.class);
    myApplication.registerService(IndexingStamp.class, IndexingStamp.class);
    myApplication.registerService(FileBasedIndexLimitsChecker.class, FileBasedIndexLimitsChecker.class);
    myApplication.registerService(FileBasedIndexTransactionMap.class, FileBasedIndexTransactionMap.class);
    myApplication.registerService(FileBasedIndexUnsavedDocumentsManager.class, FileBasedIndexUnsavedDocumentsManagerJavaComponent.class);
    myApplication.registerService(FileBasedIndexIndicesManager.class, FileBasedIndexIndicesManager.class);
    myApplication.registerService(FileBasedIndexInitializer.class, FileBasedIndexInitializerJavaComponent.class);
    myApplication.registerService(IdTableBuilding.class, IdTableBuildingJavaComponent.class);
    myApplication.registerService(CacheBuilderRegistry.class, CacheBuilderRegistryImpl.class);
    myApplication.registerService(PsiReferenceService.class, PsiReferenceServiceImpl.class);

    registerExtensionPoint(Extensions.getRootArea(), CacheBuilderEP.EP_NAME, CacheBuilderEP.class);

    //indexes
    registerExtensionPoint(Extensions.getRootArea(), FileBasedIndexExtension.EXTENSION_POINT_NAME, FileBasedIndexExtension.class);
    addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new StubUpdatingIndex(myApplication.getComponent(IndexingStamp.class)));
    addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new IdIndexCore(myApplication.getComponent(IdTableBuilding.class)));

    //addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new IdIndexCore(myApplication.getComponent(FilenameIndex.class)));
    //addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new IdIndexCore(myApplication.getComponent(FileTypeIndex.class)));
    //addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new IdIndexCore(myApplication.getComponent(TrigramIndex.class)));


    //search
    registerExtensionPoint(Extensions.getRootArea(), UseScopeEnlarger.EP_NAME, UseScopeEnlarger.class);
    registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", QueryExecutor.class);

    addExtension(new ExtensionPointName<PsiAnnotationMethodReferencesSearcher>("com.intellij.referencesSearch"), instantiateClass(
      PsiAnnotationMethodReferencesSearcher.class));
    addExtension(new ExtensionPointName<ConstructorReferencesSearcher>("com.intellij.referencesSearch"), instantiateClass(
      ConstructorReferencesSearcher.class));
    addExtension(new ExtensionPointName<SimpleAccessorReferenceSearcher>("com.intellij.referencesSearch"), instantiateClass(
      SimpleAccessorReferenceSearcher.class));
    addExtension(new ExtensionPointName<VariableInIncompleteCodeSearcher>("com.intellij.referencesSearch"), instantiateClass(
      VariableInIncompleteCodeSearcher.class));
    addExtension(new ExtensionPointName<CachesBasedRefSearcher>("com.intellij.referencesSearch"), instantiateClass(
      CachesBasedRefSearcher.class));

    //registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.PsiAnnotationMethodReferencesSearcher.class);
    //registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.ConstructorReferencesSearcher.class);
    //registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.SimpleAccessorReferenceSearcher.class);
    //registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.VariableInIncompleteCodeSearcher.class);
    //registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.CachesBasedRefSearcher.class);

    registerExtensionPoint(Extensions.getRootArea(), "com.intellij.methodReferencesSearch", QueryExecutor.class);
    addExtension(new ExtensionPointName<MethodUsagesSearcher>("com.intellij.methodReferencesSearch"), instantiateClass(
      MethodUsagesSearcher.class));

//    <extensionPoint name="allOverridingMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="annotatedElementsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="annotatedPackagesSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="classInheritorsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="deepestSuperMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="directClassInheritorsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="overridingMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="superMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="allClassesSearch" interface="com.intellij.util.QueryExecutor"/>

    ProgressManager instance = ProgressManager.getInstance();
    Object[] components = myApplication.getComponents(Object.class);

    FileBasedIndexProjectHandlerJavaComponent component = myProject.getComponent(FileBasedIndexProjectHandlerJavaComponent.class);
    component.updateCache();
  }

  private <T> T instantiateClass(Class<T> cls) {
    try {
      Constructor<T> c = cls.getDeclaredConstructor();
      c.setAccessible(true);
      T instance = c.newInstance();
      return instance;
    }
    catch (InstantiationException e) {
      throw new RuntimeException("failed to instantiate "+cls.getName(), e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException("failed to instantiate "+cls.getName(), e);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException("failed to instantiate "+cls.getName(), e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException("failed to instantiate "+cls.getName(), e);
    }
  }

  @Override
  protected DeprecatedVirtualFileSystem createLocalFileSystem() {
    return new CoreLocalFileSystemWithId();
  }

  @Override
  public void addLibraryRoot(VirtualFile file) {
    super.addLibraryRoot(file);
    FileBasedIndexProjectHandlerJavaComponent component = myProject.getComponent(FileBasedIndexProjectHandlerJavaComponent.class);
    component.updateCache();
  }

  @Override
  public void addToClasspath(File path) {
    super.addToClasspath(path);
    FileBasedIndexProjectHandlerJavaComponent component = myProject.getComponent(FileBasedIndexProjectHandlerJavaComponent.class);
    component.updateCache();
  }
}