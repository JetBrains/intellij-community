// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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

  AddLibraryDependencyFix(PsiReference reference,
                          Module currentModule,
                          Map<Library, String> libraries,
                          DependencyScope scope,
                          boolean exported) {
    super(reference);
    myCurrentModule = currentModule;
    myLibraries = libraries;
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
}