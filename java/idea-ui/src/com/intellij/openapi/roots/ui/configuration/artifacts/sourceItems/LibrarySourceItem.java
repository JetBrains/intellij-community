/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @NotNull
  @Override
  public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new LibrarySourceItemPresentation(myLibrary, context);
  }

  public boolean equals(Object obj) {
    return obj instanceof LibrarySourceItem && myLibrary.equals(((LibrarySourceItem)obj).myLibrary);
  }

  public int hashCode() {
    return myLibrary.hashCode();
  }

  @NotNull 
  public Library getLibrary() {
    return myLibrary;
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getKindOfProducedElements() {
    return LibraryPackagingElement.getKindForLibrary(myLibrary);
  }

  @Override
  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
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
          //todo[nik] disposed library should not be shown in the tree
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
