package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* @author nik
*/
public class LibraryElementType extends ComplexPackagingElementType<LibraryPackagingElement> {
  public static final LibraryElementType LIBRARY_ELEMENT_TYPE = new LibraryElementType();

  LibraryElementType() {
    super("library", CompilerBundle.message("element.type.name.library.files"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return Icons.LIBRARY_ICON;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return !getAllLibraries(context).isEmpty();
  }

  @NotNull
  public List<? extends LibraryPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                  @NotNull CompositePackagingElement<?> parent) {
    final List<Library> selected = context.chooseLibraries(getAllLibraries(context), ProjectBundle.message("dialog.title.packaging.choose.library"));
    final List<LibraryPackagingElement> elements = new ArrayList<LibraryPackagingElement>();
    for (Library library : selected) {
      elements.add(new LibraryPackagingElement(library.getTable().getTableLevel(), library.getName(), null));
    }
    return elements;
  }

  private static List<Library> getAllLibraries(ArtifactEditorContext context) {
    List<Library> libraries = new ArrayList<Library>();
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries()));
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable(context.getProject()).getLibraries()));
    return libraries;
  }

  @NotNull
  public LibraryPackagingElement createEmpty(@NotNull Project project) {
    return new LibraryPackagingElement();
  }

  @Override
  public String getShowContentActionText() {
    return "Show Library Files";
  }
}
