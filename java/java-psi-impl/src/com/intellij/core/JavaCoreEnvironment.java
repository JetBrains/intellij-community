/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.EmptySubstitutorImpl;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory;
import com.intellij.psi.impl.compiled.DefaultClsStubBuilderFactory;
import com.intellij.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.impl.source.tree.CoreJavaASTFactory;
import com.intellij.psi.stubs.BinaryFileStubBuilders;

import java.io.File;

/**
 * @author yole
 */
public class JavaCoreEnvironment extends CoreEnvironment {
  private final CoreJavaFileManager myFileManager;
  
  public JavaCoreEnvironment(Disposable parentDisposable) {
    super(parentDisposable);

    registerFileType(JavaClassFileType.INSTANCE, "class");
    addExplicitExtension(FileTypeFileViewProviders.INSTANCE, JavaClassFileType.INSTANCE,  new ClassFileViewProviderFactory());
    addExplicitExtension(BinaryFileStubBuilders.INSTANCE, JavaClassFileType.INSTANCE, new ClassFileStubBuilder());
    
    registerFileType(JavaFileType.INSTANCE, "java");
    addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new CoreJavaASTFactory());
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, JavaLanguage.INSTANCE, new JavaParserDefinition());

    registerProjectExtensionPoint(PsiElementFinder.EP_NAME, PsiElementFinder.class);
    registerExtensionPoint(Extensions.getRootArea(), ClsStubBuilderFactory.EP_NAME, ClsStubBuilderFactory.class);
    registerExtensionPoint(Extensions.getRootArea(), PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
    registerExtensionPoint(Extensions.getRootArea(), JavaMainMethodProvider.EP_NAME, JavaMainMethodProvider.class);
    addExtension(ClsStubBuilderFactory.EP_NAME, new DefaultClsStubBuilderFactory());

    myApplication.registerService(PsiPackageImplementationHelper.class, new CorePsiPackageImplementationHelper());

    myFileManager = new CoreJavaFileManager(myPsiManager, getLocalFileSystem(), myJarFileSystem);
    myProject.registerService(PsiElementFactory.class, new PsiElementFactoryImpl(myPsiManager));
    myProject.registerService(JavaPsiImplementationHelper.class, new CoreJavaPsiImplementationHelper());
    myProject.registerService(PsiResolveHelper.class, new PsiResolveHelperImpl(myPsiManager));
    myProject.registerService(LanguageLevelProjectExtension.class, new CoreLanguageLevelProjectExtension());
    myProject.registerService(PackageIndex.class, myFileManager);
    myProject.registerService(JavaResolveCache.class, new JavaResolveCache(null));

    JavaPsiFacadeImpl javaPsiFacade = new JavaPsiFacadeImpl(myProject, myPsiManager, myFileManager, null);
    myProject.registerService(CoreJavaFileManager.class, myFileManager);
    registerComponentInstance(myProject.getPicoContainer(),
            JavaPsiFacade.class,
            javaPsiFacade);
    myProject.registerService(JavaPsiFacade.class, javaPsiFacade);

    myApplication.registerService(EmptySubstitutor.class, new EmptySubstitutorImpl());
    myApplication.registerService(JavaDirectoryService.class, new CoreJavaDirectoryService());
    myApplication.registerService(JavaVersionService.class, new JavaVersionService());
  }

  public void addToClasspath(File path) {
    final VirtualFile root = path.isFile()
                             ? myJarFileSystem.findFileByPath(path + "!/")
                             : getLocalFileSystem().findFileByPath(path.getPath());

    if (root != null) {
      myFileManager.addToClasspath(path);
      myFileIndexFacade.addLibraryRoot(root);
    }
    else {
      throw new IllegalArgumentException("trying to add non-existing file to classpath: " + path);
    }
  }
}
