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

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.EmptySubstitutorImpl;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
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

    registerProjectExtensionPoint(PsiElementFinder.EP_NAME, PsiElementFinder.class);
    registerExtensionPoint(Extensions.getRootArea(), ClsStubBuilderFactory.EP_NAME, ClsStubBuilderFactory.class);

    myFileManager = new CoreJavaFileManager(myPsiManager, getLocalFileSystem(), myJarFileSystem);
    JavaPsiFacadeImpl javaPsiFacade = new JavaPsiFacadeImpl(myProject, myPsiManager, myFileManager, null);
    registerComponentInstance(myProject.getPicoContainer(),
                              JavaPsiFacade.class,
                              javaPsiFacade);
    myProject.registerService(JavaPsiFacade.class, javaPsiFacade);
    myProject.registerService(PsiElementFactory.class, new PsiElementFactoryImpl(myPsiManager));
    myProject.registerService(JavaPsiImplementationHelper.class, new CoreJavaPsiImplementationHelper());
    myProject.registerService(PsiResolveHelper.class, new PsiResolveHelperImpl(myPsiManager));
    myProject.registerService(LanguageLevelProjectExtension.class, new CoreLanguageLevelProjectExtension());

    myApplication.registerService(EmptySubstitutor.class, new EmptySubstitutorImpl());
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
