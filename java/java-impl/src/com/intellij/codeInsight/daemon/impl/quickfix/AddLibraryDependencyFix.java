// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReference;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

class AddLibraryDependencyFix extends OrderEntryFix {
  private final Module myCurrentModule;
  private final @Unmodifiable Map<Library, String> myLibraries;
  private final DependencyScope myScope;
  private final boolean myExported;

  AddLibraryDependencyFix(@NotNull PsiReference reference,
                          @NotNull Module currentModule,
                          @Unmodifiable @NotNull Map<Library, String> libraries,
                          @NotNull DependencyScope scope,
                          boolean exported) {
    super(reference);
    myCurrentModule = currentModule;
    myLibraries = Map.copyOf(libraries);
    myScope = scope;
    myExported = exported;
  }

  @Override
  public @NotNull String getText() {
    if (myLibraries.size() == 1) {
      return QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", getLibraryName(ContainerUtil.getFirstItem(myLibraries.keySet())));
    }
    return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath.options");
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return !project.isDisposed() && !myCurrentModule.isDisposed() && !myLibraries.isEmpty() && !ContainerUtil.exists(myLibraries.keySet(), l -> ((LibraryEx)l).isDisposed());
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile psiFile) {
    if (myLibraries.size() == 1) {
      addLibrary(project, editor, ContainerUtil.getFirstItem(myLibraries.keySet()));
    }
    else {
      JBPopup popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(new ArrayList<>(myLibraries.keySet()))
        .setRenderer(new SimpleListCellRenderer<>() {
          @Override
          public void customize(@NotNull JList<? extends Library> list, Library lib, int index, boolean selected, boolean hasFocus) {
            if (lib != null) {
              setText(lib.getPresentableName());
              setIcon(AllIcons.Nodes.PpLib);
            }
          }
        })
        .setTitle(QuickFixBundle.message("popup.title.choose.library.to.add.dependency.on"))
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback(selectedValue -> addLibrary(project, editor, selectedValue))
        .createPopup();
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      }
      else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void addLibrary(@NotNull Project project, @Nullable Editor editor, Library library) {
    ModalityState modality = ModalityState.defaultModalityState();
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(myCurrentModule, library, myScope, myExported)
      .onSuccess(__ -> {
        if (editor == null) return;
        ReadAction.nonBlocking(() -> {
            PsiReference reference = restoreReference();
            String qName = myLibraries.get(library);
            if (qName.isEmpty() || reference == null) return null;
            return AddImportAction.create(editor, myCurrentModule, reference, qName);
          })
          .expireWhen(() -> editor.isDisposed() || myCurrentModule.isDisposed())
          .finishOnUiThread(modality, action -> {
            if (action != null) {
              action.execute();
            }
          })
          .submit(AppExecutorUtil.getAppExecutorService());
      });
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    Library firstItem = ContainerUtil.getFirstItem(myLibraries.keySet());
    String fqName = myLibraries.get(firstItem);
    PsiReference reference = restoreReference();
    String refName = reference != null && reference.getElement().isPhysical() 
                     && !StringUtil.isEmpty(fqName) ? StringUtil.getShortName(fqName) : null;

    String libraryList = NlsMessages.formatAndList(ContainerUtil.map(myLibraries.keySet(), library -> "'" + getLibraryName(library) + "'"));
    String libraryName = getLibraryName(firstItem);
    String message = refName != null ? JavaBundle.message("adds.library.preview", myLibraries.size(), libraryName, libraryList, myCurrentModule.getName(), refName)
                                     : JavaBundle.message("adds.library.preview.no.import", myLibraries.size(), libraryName, libraryList, myCurrentModule.getName());
    return new IntentionPreviewInfo.Html(HtmlChunk.text(message));
  }

  private @NlsSafe String getLibraryName(@NotNull Library library) {
    final PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByLibrary(library, myCurrentModule.getProject());
    if (javaModule != null && PsiNameHelper.isValidModuleName(javaModule.getName(), javaModule)) {
      return javaModule.getName();
    } else {
      return library.getPresentableName();
    }
  }
}