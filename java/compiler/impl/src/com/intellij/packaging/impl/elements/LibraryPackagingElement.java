package com.intellij.packaging.impl.elements;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.ui.LibraryElementPresentation;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class LibraryPackagingElement extends ComplexPackagingElement<LibraryPackagingElement> {
  private String myLevel;
  private String myName;

  public LibraryPackagingElement() {
    super(LibraryElementType.LIBRARY_ELEMENT_TYPE);
  }

  public LibraryPackagingElement(String level, String name) {
    super(LibraryElementType.LIBRARY_ELEMENT_TYPE);
    myLevel = level;
    myName = name;
  }

  public List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType) {
    final Library library = findLibrary(context);
    if (library != null) {
      final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      final List<PackagingElement<?>> elements = new ArrayList<PackagingElement<?>>();
      for (VirtualFile file : files) {
        elements.add(new FileCopyPackagingElement(FileUtil.toSystemIndependentName(PathUtil.getLocalPath(file))));
      }
      return elements;
    }
    return null;
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    final Library library = findLibrary(context);
    return library != null ? LibrarySourceItem.getKindForLibrary(library) : PackagingElementOutputKind.OTHER;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new LibraryElementPresentation(myLevel, myName, findLibrary(context), context);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    if (!(element instanceof LibraryPackagingElement)) {
      return false;
    }

    LibraryPackagingElement packagingElement = (LibraryPackagingElement)element;
    return myLevel != null && myName != null && myLevel.equals(packagingElement.getLevel())
           && myName.equals(packagingElement.getName());
  }

  public LibraryPackagingElement getState() {
    return this;
  }

  public void loadState(LibraryPackagingElement state) {
    myLevel = state.getLevel();
    myName = state.getName();
  }

  @Attribute("level")
  public String getLevel() {
    return myLevel;
  }

  public void setLevel(String level) {
    myLevel = level;
  }

  @Attribute("name")
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "lib:" + myName + "(" + myLevel + ")";
  }

  public void setName(String name) {
    myName = name;
  }

  @Nullable
  public Library findLibrary(@NotNull PackagingElementResolvingContext context) {
    return LibraryLink.findLibrary(myName, myLevel, context.getProject());
  }
}
