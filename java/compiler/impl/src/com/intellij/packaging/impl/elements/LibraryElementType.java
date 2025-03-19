// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryElementType extends ComplexPackagingElementType<LibraryPackagingElement> {
  public static final LibraryElementType LIBRARY_ELEMENT_TYPE = new LibraryElementType();

  LibraryElementType() {
    super("library", JavaCompilerBundle.messagePointer("element.type.name.library.files"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return PlatformIcons.LIBRARY_ICON;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return !getAllLibraries(context).isEmpty();
  }

  @Override
  public @NotNull List<? extends LibraryPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                          @NotNull CompositePackagingElement<?> parent) {
    final List<Library> selected = context.chooseLibraries(JavaCompilerBundle.message("dialog.title.packaging.choose.library"));
    final List<LibraryPackagingElement> elements = new ArrayList<>();
    for (Library library : selected) {
      elements.add(new LibraryPackagingElement(library.getTable().getTableLevel(), library.getName(), null));
    }
    return elements;
  }

  private static List<Library> getAllLibraries(ArtifactEditorContext context) {
    List<Library> libraries = new ArrayList<>();
    ContainerUtil.addAll(libraries, LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries());
    ContainerUtil.addAll(libraries, LibraryTablesRegistrar.getInstance().getLibraryTable(context.getProject()).getLibraries());
    return libraries;
  }

  @Override
  public @NotNull LibraryPackagingElement createEmpty(@NotNull Project project) {
    return new LibraryPackagingElement();
  }

  @Override
  public String getShowContentActionText() {
    return JavaCompilerBundle.message("show.library.files");
  }
}
