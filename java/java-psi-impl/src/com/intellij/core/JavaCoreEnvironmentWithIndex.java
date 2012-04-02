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

import com.intellij.core.indexing.AbstractVfsAdapterJavaComponent;
import com.intellij.core.indexing.FileBasedIndexInitializerJavaComponent;
import com.intellij.core.indexing.FileBasedIndexJavaComponent;
import com.intellij.core.indexing.FileBasedIndexUnsavedDocumentsManagerJavaComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystemWithId;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilService;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.psi.stubs.SerializationManagerImpl;
import com.intellij.util.indexing.*;

public class JavaCoreEnvironmentWithIndex extends JavaCoreEnvironment {

  public JavaCoreEnvironmentWithIndex(Disposable parentDisposable) {
    super(parentDisposable);

    registerExtensionPoint(Extensions.getRootArea(), FileBasedIndexExtension.EXTENSION_POINT_NAME, FileBasedIndexExtension.class);

    registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.PsiAnnotationMethodReferencesSearcher.class);
    registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.ConstructorReferencesSearcher.class);
    registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.SimpleAccessorReferenceSearcher.class);
    registerExtensionPoint(Extensions.getRootArea(), "com.intellij.referencesSearch", com.intellij.psi.impl.search.VariableInIncompleteCodeSearcher.class);



//    <extensionPoint name="allOverridingMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="annotatedElementsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="annotatedPackagesSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="classInheritorsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="deepestSuperMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="directClassInheritorsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="methodReferencesSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="overridingMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="superMethodsSearch" interface="com.intellij.util.QueryExecutor"/>
//    <extensionPoint name="allClassesSearch" interface="com.intellij.util.QueryExecutor"/>

    myApplication.registerService(PsiManager.class, PsiManagerImpl.class);
    myApplication.registerService(InjectedLanguageUtilService.class, InjectedLanguageUtilServiceJavaComponent.class);
    myApplication.registerService(PsiSearchHelper.class, PsiSearchHelperJavaComponent.class);

    myApplication.registerService(SerializationManager.class, SerializationManagerImpl.class);
    myApplication.registerService(AbstractVfsAdapter.class, new AbstractVfsAdapterJavaComponent((CoreLocalFileSystemWithId)getLocalFileSystem()));
    myApplication.registerService(FileBasedIndex.class, FileBasedIndexJavaComponent.class);
    myApplication.registerService(IndexingStamp.class, IndexingStamp.class);
    myApplication.registerService(FileBasedIndexLimitsChecker.class, FileBasedIndexLimitsChecker.class);
    myApplication.registerService(FileBasedIndexTransactionMap.class, FileBasedIndexTransactionMap.class);
    myApplication.registerService(FileBasedIndexUnsavedDocumentsManager.class, FileBasedIndexUnsavedDocumentsManagerJavaComponent.class);
    myApplication.registerService(FileBasedIndexIndicesManager.class, FileBasedIndexIndicesManager.class);
    myApplication.registerService(FileBasedIndexInitializer.class, FileBasedIndexInitializerJavaComponent.class);
  }

  @Override
  protected DeprecatedVirtualFileSystem createLocalFileSystem() {
    return new CoreLocalFileSystemWithId();
  }
}