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

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.mock.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationComponentLocator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiCachedValuesFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactoryImpl;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.Function;
import org.picocontainer.MutablePicoContainer;

/**
 * @author yole
 */
public class CoreEnvironment {
  private CoreFileTypeRegistry myFileTypeRegistry;
  private CoreEncodingRegistry myEncodingRegistry;
  private MockApplication myApplication;
  private MockProject myProject;
  private CoreLocalFileSystem myLocalFileSystem;
  private MockFileIndexFacade myFileIndexFacade;

  public CoreEnvironment(Disposable parentDisposable) {
    myFileTypeRegistry = new CoreFileTypeRegistry();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    FileTypeRegistry.ourInstanceGetter = new Getter<FileTypeRegistry>() {
      @Override
      public FileTypeRegistry get() {
        return myFileTypeRegistry;
      }
    };

    myEncodingRegistry = new CoreEncodingRegistry();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    EncodingRegistry.ourInstanceGetter = new Getter<EncodingRegistry>() {
      @Override
      public EncodingRegistry get() {
        return myEncodingRegistry;
      }
    };

    myApplication = new MockApplication(parentDisposable);
    new ApplicationManager() {{
      ourApplication = myApplication;
    }};
    ApplicationComponentLocator.setInstance(myApplication);
    myLocalFileSystem = new CoreLocalFileSystem();

    myProject = new MockProject(myApplication.getPicoContainer(), parentDisposable);

    final MutablePicoContainer appContainer = myApplication.getPicoContainer();
    registerComponentInstance(appContainer, FileDocumentManager.class, new MockFileDocumentManagerImpl(new Function<CharSequence, Document>() {
      @Override
      public Document fun(CharSequence charSequence) {
        return new DocumentImpl(charSequence);
      }
    }, null));

    myApplication.registerService(DefaultASTFactory.class, new CoreASTFactory());
    myApplication.registerService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    myApplication.registerService(ReferenceProvidersRegistry.class, new MockReferenceProvidersRegistry());

    myFileIndexFacade = new MockFileIndexFacade(myProject);
    final MutablePicoContainer projectContainer = myProject.getPicoContainer();
    PsiManagerImpl psiManager = new PsiManagerImpl(myProject, null, null, myFileIndexFacade, null);
    ((FileManagerImpl) psiManager.getFileManager()).markInitialized();
    registerComponentInstance(projectContainer, PsiManager.class, psiManager);

    myProject.registerService(PsiFileFactory.class, new PsiFileFactoryImpl(psiManager));
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(psiManager)));
    myProject.registerService(PsiDirectoryFactory.class, new PsiDirectoryFactoryImpl(psiManager));
  }

  public Project getProject() {
    return myProject;
  }

  public void registerFileType(FileType fileType, String extension) {
    myFileTypeRegistry.registerFileType(fileType, extension);
  }

  public void registerParserDefinition(ParserDefinition definition) {
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
  }

  protected <T> void registerComponentInstance(final MutablePicoContainer container, final Class<T> key, final T implementation) {
    container.unregisterComponent(key);
    container.registerComponentInstance(key, implementation);
  }

  protected <T> void addExplicitExtension(final LanguageExtension<T> instance, final Language language, final T object) {
    instance.addExplicitExtension(language, object);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        instance.removeExplicitExtension(language, object);
      }
    });
  }

  public CoreLocalFileSystem getLocalFileSystem() {
    return myLocalFileSystem;
  }
}
