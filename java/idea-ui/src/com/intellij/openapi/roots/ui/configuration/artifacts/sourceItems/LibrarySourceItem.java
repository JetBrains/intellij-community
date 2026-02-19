// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import com.intellij.packaging.impl.ui.LibraryElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LibrarySourceItem extends PackagingSourceItem {
  private final Library myLibrary;

  public LibrarySourceItem(@NotNull Library library) {
    myLibrary = library;
  }

  @Override
  public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new LibrarySourceItemPresentation(myLibrary, context);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LibrarySourceItem && myLibrary.equals(((LibrarySourceItem)obj).myLibrary);
  }

  @Override
  public int hashCode() {
    return myLibrary.hashCode();
  }

  public @NotNull Library getLibrary() {
    return myLibrary;
  }

  @Override
  public @NotNull PackagingElementOutputKind getKindOfProducedElements() {
    return LibraryPackagingElement.getKindForLibrary(myLibrary);
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    return PackagingElementFactory.getInstance().createLibraryElements(myLibrary);
  }

  private static class LibrarySourceItemPresentation extends SourceItemPresentation {
    private final Library myLibrary;
    private final ArtifactEditorContext myContext;

    LibrarySourceItemPresentation(Library library, ArtifactEditorContext context) {
      myLibrary = library;
      myContext = context;
    }

    @Override
    public boolean canNavigateToSource() {
      return myLibrary != null;
    }

    @Override
    public void navigateToSource() {
      myContext.selectLibrary(myLibrary);
    }

    @Override
    public String getPresentableName() {
      return myLibrary.getPresentableName();
    }

    @Override
    public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                       SimpleTextAttributes commentAttributes) {
      final String name = myLibrary.getName();
      if (name != null) {
        presentationData.setIcon(PlatformIcons.LIBRARY_ICON);
        presentationData.addText(name, mainAttributes);
        presentationData.addText(LibraryElementPresentation.getLibraryTableComment(myLibrary), commentAttributes);
      }
      else {
        if (((LibraryEx)myLibrary).isDisposed()) {
          //todo disposed library should not be shown in the tree
          presentationData.addText(JavaUiBundle.message("library.source.item.label.invalid.library"), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        final VirtualFile[] files = myLibrary.getFiles(OrderRootType.CLASSES);
        if (files.length > 0) {
          final VirtualFile file = files[0];
          presentationData.setIcon(VirtualFilePresentation.getIcon(file));
          presentationData.addText(file.getName(), mainAttributes);
        }
        else {
          presentationData.addText(JavaUiBundle.message("library.source.item.label.empty.library"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
      }
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.LIBRARY_WEIGHT;
    }
  }
}
