// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

class AddLibraryDependencyFix extends OrderEntryFix {
  private final Module myCurrentModule;
  private final Map<Library, String> myLibraries;
  private final DependencyScope myScope;
  private final boolean myExported;

  AddLibraryDependencyFix(@NotNull PsiReference reference,
                          @NotNull Module currentModule,
                          @NotNull Map<Library, String> libraries,
                          @NotNull DependencyScope scope,
                          boolean exported) {
    super(reference);
    myCurrentModule = currentModule;
    myLibraries = ContainerUtil.<Library, String>immutableMapBuilder().putAll(libraries).build();
    myScope = scope;
    myExported = exported;
  }

  @Override
  @NotNull
  public String getText() {
    if (myLibraries.size() == 1) {
      return QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", ContainerUtil.getFirstItem(myLibraries.keySet()).getPresentableName());
    }
    return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath.options");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed() && !myLibraries.isEmpty() && !ContainerUtil.exists(myLibraries.keySet(), l -> ((LibraryEx)l).isDisposed());
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
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
        .setItemChosenCallback((selectedValue) -> addLibrary(project, editor, selectedValue))
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
    JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, library, myScope, myExported);

    String qName = myLibraries.get(library);
    if (qName != null && editor != null) {
      importClass(myCurrentModule, editor, restoreReference(), qName);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    Library firstItem = ContainerUtil.getFirstItem(myLibraries.keySet());
    String fqName = myLibraries.get(firstItem);
    String refName = fqName != null ? StringUtil.getShortName(fqName) : null;

    String libraryList = NlsMessages.formatAndList(ContainerUtil.map2List(myLibraries.keySet(), library -> "'" + library.getPresentableName() + "'"));
    String libraryName = firstItem.getPresentableName();
    String message = refName != null ? JavaBundle.message("adds.library.preview", myLibraries.size(), libraryName, libraryList, myCurrentModule.getName(), refName)
                                     : JavaBundle.message("adds.library.preview.no.import", myLibraries.size(), libraryName, libraryList, myCurrentModule.getName());
    return new IntentionPreviewInfo.Html(HtmlChunk.text(message));
  }
}