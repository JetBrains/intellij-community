/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class TemplateLanguageStructureViewBuilder implements StructureViewBuilder {
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  @Nullable private Language myTemplateDataLanguage;
  private StructureViewComposite.StructureViewDescriptor myBaseStructureViewDescriptor;
  private FileEditor myFileEditor;
  private StructureViewComposite myStructureViewComposite;
  private int myBaseLanguageViewDescriptorIndex;

  protected TemplateLanguageStructureViewBuilder(PsiElement psiElement) {
    myVirtualFile = psiElement.getContainingFile().getVirtualFile();
    myProject = psiElement.getProject();
    myTemplateDataLanguage = getTemplateDataLanguage();
  }

  private void updateAfterPsiChange() {
    if (myProject.isDisposed()) return;
    if (((StructureViewComponent)myBaseStructureViewDescriptor.structureView).isDisposed()) return;
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!myVirtualFile.isValid() || getViewProvider() == null) return;

      StructureViewWrapper structureViewWrapper = StructureViewFactoryEx.getInstanceEx(myProject).getStructureViewWrapper();
      if (structureViewWrapper == null) return;

      Language baseLanguage = getTemplateDataLanguage();
      if (baseLanguage == myTemplateDataLanguage
          && (myBaseStructureViewDescriptor == null || isPsiValid(myBaseStructureViewDescriptor))) {
        updateBaseLanguageView();
      }
      else {
        myTemplateDataLanguage = baseLanguage;
        ((StructureViewWrapperImpl)structureViewWrapper).rebuild();
      }
    });
  }

  private static boolean isPsiValid(@NotNull StructureViewComposite.StructureViewDescriptor baseStructureViewDescriptor) {
    StructureViewComponent view = (StructureViewComponent)baseStructureViewDescriptor.structureView;
    if (view.isDisposed()) return false;
    Object value = StructureViewComponent.unwrapValue(view.getTree().getModel().getRoot());
    return !(value instanceof PsiElement && !((PsiElement)value).isValid());
  }

  @Nullable
  private FileViewProvider getViewProvider() {
    return PsiManager.getInstance(myProject).findViewProvider(myVirtualFile);
  }

  @Nullable
  private Language getTemplateDataLanguage() {
    FileViewProvider provider = getViewProvider();
    return provider instanceof TemplateLanguageFileViewProvider
           ? ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage() : null;
  }

  private void updateBaseLanguageView() {
    if (myBaseStructureViewDescriptor == null || !myProject.isOpen()) return;
    final StructureViewComponent view = (StructureViewComponent)myBaseStructureViewDescriptor.structureView;
    if (view.isDisposed()) return;

    TreeState treeState = TreeState.createOn(view.getTree(), new TreePath(view.getTree().getModel().getRoot()));
    updateTemplateDataFileView();

    if (view.isDisposed()) return;
    treeState.applyTo(view.getTree());
  }

  @Override
  @NotNull
  public StructureView createStructureView(FileEditor fileEditor, @NotNull Project project) {
    myFileEditor = fileEditor;
    List<StructureViewComposite.StructureViewDescriptor> viewDescriptors = new ArrayList<>();
    final FileViewProvider provider = getViewProvider();
    assert provider != null : myVirtualFile;

    final StructureViewComposite.StructureViewDescriptor structureViewDescriptor = createMainView(fileEditor, provider.getPsi(provider.getBaseLanguage()));
    if (structureViewDescriptor != null) viewDescriptors.add(structureViewDescriptor);

    myBaseLanguageViewDescriptorIndex = -1;

    updateTemplateDataFileView();
    if (myBaseStructureViewDescriptor != null) {
      viewDescriptors.add(myBaseStructureViewDescriptor);
      myBaseLanguageViewDescriptorIndex = viewDescriptors.size() - 1;
    }

    if (provider instanceof TemplateLanguageFileViewProvider) {
      final Language dataLanguage = ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage();
      for (final Language language : provider.getLanguages()) {
        if (language != dataLanguage && language != provider.getBaseLanguage()) {
          ContainerUtil.addIfNotNull(viewDescriptors, createBaseLanguageStructureView(fileEditor, language));
        }
      }
    }

    StructureViewComposite.StructureViewDescriptor[] array = viewDescriptors.toArray(new StructureViewComposite.StructureViewDescriptor[viewDescriptors.size()]);
    myStructureViewComposite = new StructureViewComposite(array);
    project.getMessageBus().connect(myStructureViewComposite).subscribe(PsiModificationTracker.TOPIC, new PsiModificationTracker.Listener() {
      final Alarm alarm = new Alarm(myStructureViewComposite);

      @Override
      public void modificationCountChanged() {
        alarm.cancelAllRequests();
        alarm.addRequest(() -> updateAfterPsiChange(), 300, ModalityState.NON_MODAL);
      }
    });
    return myStructureViewComposite;
  }

  protected abstract StructureViewComposite.StructureViewDescriptor createMainView(FileEditor fileEditor, PsiFile mainFile);

  @Nullable
  private StructureViewComposite.StructureViewDescriptor createBaseLanguageStructureView(final FileEditor fileEditor, final Language language) {
    if (!myVirtualFile.isValid()) return null;

    final FileViewProvider viewProvider = getViewProvider();
    if (viewProvider == null) return null;

    final PsiFile dataFile = viewProvider.getPsi(language);
    if (dataFile == null || !isAcceptableBaseLanguageFile(dataFile)) return null;

    final PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(language);
    if (factory == null) return null;

    final StructureViewBuilder builder = factory.getStructureViewBuilder(dataFile);
    if (builder == null) return null;

    StructureView structureView = builder.createStructureView(fileEditor, myProject);
    return new StructureViewComposite.StructureViewDescriptor(IdeBundle.message("tab.structureview.baselanguage.view", language.getDisplayName()), structureView, findFileType(language).getIcon());
  }

  protected boolean isAcceptableBaseLanguageFile(PsiFile dataFile) {
    return true;
  }

  private void updateTemplateDataFileView() {
    final Language newDataLanguage = getTemplateDataLanguage();

    if (myBaseStructureViewDescriptor != null) {
      if (myTemplateDataLanguage == newDataLanguage) return;

      Disposer.dispose(myBaseStructureViewDescriptor.structureView);
    }

    if (newDataLanguage != null) {
      myBaseStructureViewDescriptor = createBaseLanguageStructureView(myFileEditor, newDataLanguage);
      if (myStructureViewComposite != null) {
        myStructureViewComposite.setStructureView(myBaseLanguageViewDescriptorIndex, myBaseStructureViewDescriptor);
      }
    }
  }

  @NotNull
  private static FileType findFileType(final Language language) {
    FileType[] registeredFileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : registeredFileTypes) {
      if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() == language) {
        return fileType;
      }
    }
    return FileTypes.UNKNOWN;
  }
}
