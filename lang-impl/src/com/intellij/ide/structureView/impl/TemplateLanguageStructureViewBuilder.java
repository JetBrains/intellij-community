/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.structureView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class TemplateLanguageStructureViewBuilder implements StructureViewBuilder {
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  private PsiTreeChangeAdapter myPsiTreeChangeAdapter;
  private Language myTemplateDataLanguage;
  private StructureViewComposite.StructureViewDescriptor myBaseStructureViewDescriptor;
  private FileEditor myFileEditor;
  private StructureViewComposite myStructureViewComposite;
  private int myBaseLanguageViewDescriptorIndex;

  protected TemplateLanguageStructureViewBuilder(PsiElement psiElement) {
    myVirtualFile = psiElement.getContainingFile().getVirtualFile();
    myProject = psiElement.getProject();

    installBaseLanguageListener();
  }

  private void installBaseLanguageListener() {
    myPsiTreeChangeAdapter = new PsiTreeChangeAdapter() {
      public void childAdded(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childMoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      final Alarm myAlarm = new Alarm();
      public void childrenChanged(PsiTreeChangeEvent event) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable(){
          public void run() {
            if (myProject.isDisposed()) return;
            if (myBaseStructureViewDescriptor != null && ((StructureViewComponent)myBaseStructureViewDescriptor.structureView).getTree() == null) return;
            if (!myVirtualFile.isValid()) return;
            ApplicationManager.getApplication().runReadAction(new Runnable(){
              public void run() {
                Language baseLanguage = getViewProvider().getTemplateDataLanguage();
                if (baseLanguage == myTemplateDataLanguage) {
                  updateBaseLanguageView();
                }
                else {
                  myTemplateDataLanguage = baseLanguage;
                  StructureViewWrapper structureViewWrapper = StructureViewFactoryEx.getInstanceEx(myProject).getStructureViewWrapper();
                  ((StructureViewWrapperImpl)structureViewWrapper).rebuild();
                  ((ProjectViewImpl)ProjectView.getInstance(myProject)).rebuildStructureViewPane();
                }
              }
            });
          }
        }, 300, ModalityState.NON_MODAL);
      }
    };
    myTemplateDataLanguage = getViewProvider().getTemplateDataLanguage();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter);
  }

  @Nullable
  private TemplateLanguageFileViewProvider getViewProvider() {
    return (TemplateLanguageFileViewProvider)PsiManager.getInstance(myProject).findViewProvider(myVirtualFile);
  }

  private void updateBaseLanguageView() {
    if (myBaseStructureViewDescriptor == null || !myProject.isOpen()) return;
    final StructureViewComponent view = (StructureViewComponent)myBaseStructureViewDescriptor.structureView;
    if (view.isDisposed()) return;

    StructureViewState state = view.getState();
    List<PsiAnchor> expanded = collectAnchors(state.getExpandedElements());
    List<PsiAnchor> selected = collectAnchors(state.getSelectedElements());
    updateTemplateDataFileView();

    if (view.isDisposed()) return;

    for (PsiAnchor pointer : expanded) {
      PsiElement element = pointer.retrieve();
      if (element != null) {
        view.expandPathToElement(element);
      }
    }
    for (PsiAnchor pointer : selected) {
      PsiElement element = pointer.retrieve();
      if (element != null) {
        view.addSelectionPathTo(element);
      }
    }
  }

  private static List<PsiAnchor> collectAnchors(final Object[] expandedElements) {
    List<PsiAnchor> expanded = new ArrayList<PsiAnchor>(expandedElements == null ? 0 : expandedElements.length);
    if (expandedElements != null) {
      for (Object element : expandedElements) {
        if (element instanceof PsiElement && ((PsiElement) element).isValid()) {
          expanded.add(PsiAnchor.create((PsiElement)element));
        }
      }
    }
    return expanded;
  }

  private void removeBaseLanguageListener() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeAdapter);
  }

  @NotNull
  public StructureView createStructureView(FileEditor fileEditor, Project project) {
    myFileEditor = fileEditor;
    List<StructureViewComposite.StructureViewDescriptor> viewDescriptors = new ArrayList<StructureViewComposite.StructureViewDescriptor>();
    final TemplateLanguageFileViewProvider provider = getViewProvider();
    assert provider != null;

    final StructureViewComposite.StructureViewDescriptor structureViewDescriptor = createMainView(fileEditor, provider.getPsi(provider.getBaseLanguage()));
    if (structureViewDescriptor != null) viewDescriptors.add(structureViewDescriptor);

    myBaseLanguageViewDescriptorIndex = -1;
    final Language dataLanguage = provider.getTemplateDataLanguage();

    updateTemplateDataFileView();
    if (myBaseStructureViewDescriptor != null) {
      viewDescriptors.add(myBaseStructureViewDescriptor);
      myBaseLanguageViewDescriptorIndex = viewDescriptors.size() - 1;
    }

    for (final Language language : getViewProvider().getLanguages()) {
      if (language != dataLanguage && language != getViewProvider().getBaseLanguage()) {
        ContainerUtil.addIfNotNull(createBaseLanguageStructureView(fileEditor, language), viewDescriptors);
      }
    }

    StructureViewComposite.StructureViewDescriptor[] array = viewDescriptors.toArray(new StructureViewComposite.StructureViewDescriptor[viewDescriptors.size()]);
    myStructureViewComposite = new StructureViewComposite(array){
      public void dispose() {
        removeBaseLanguageListener();
        super.dispose();
      }
    };
    return myStructureViewComposite;
  }

  protected abstract StructureViewComposite.StructureViewDescriptor createMainView(FileEditor fileEditor, PsiFile mainFile);

  @Nullable
  private StructureViewComposite.StructureViewDescriptor createBaseLanguageStructureView(final FileEditor fileEditor, final Language language) {
    if (!myVirtualFile.isValid()) return null;

    final TemplateLanguageFileViewProvider viewProvider = getViewProvider();
    if (viewProvider == null) return null;

    final PsiFile dataFile = viewProvider.getPsi(language);
    if (dataFile == null) return null;

    final PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(language);
    if (factory == null) return null;

    final StructureViewBuilder builder = factory.getStructureViewBuilder(dataFile);
    if (builder == null) return null;

    StructureView structureView = builder.createStructureView(fileEditor, myProject);
    return new StructureViewComposite.StructureViewDescriptor(IdeBundle.message("tab.structureview.baselanguage.view", language.getDisplayName()), structureView, findFileType(language).getIcon());
  }

  private void updateTemplateDataFileView() {
    new WriteCommandAction(myProject) {
      protected void run(Result result) throws Throwable {
        final TemplateLanguageFileViewProvider provider = getViewProvider();
        final Language newDataLanguage = provider == null ? null : provider.getTemplateDataLanguage();

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
    }.execute();
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
