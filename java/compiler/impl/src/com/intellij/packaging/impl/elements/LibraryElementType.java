package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.packaging.ChooseLibrariesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
* @author nik
*/
public class LibraryElementType extends PackagingElementType<LibraryPackagingElement> {
  public static final LibraryElementType LIBRARY_ELEMENT_TYPE = new LibraryElementType();

  LibraryElementType() {
    super("library", CompilerBundle.message("element.type.name.library.files"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return Icons.LIBRARY_ICON;
  }

  @Override
  public boolean canCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact) {
    return !getAllLibraries(context).isEmpty();
  }

  @NotNull
  public List<? extends LibraryPackagingElement> chooseAndCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact,
                                                                  @NotNull CompositePackagingElement<?> parent) {
    ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(context.getProject(), getAllLibraries(context),
                                                             ProjectBundle.message("dialog.title.packaging.choose.library"), "");
    dialog.show();
    final List<Library> selected = dialog.getChosenElements();
    final List<LibraryPackagingElement> elements = new ArrayList<LibraryPackagingElement>();
    if (dialog.isOK()) {
      for (Library library : selected) {
        elements.add(new LibraryPackagingElement(library.getTable().getTableLevel(), library.getName()));
      }
    }
    return elements;
  }

  private List<Library> getAllLibraries(PackagingEditorContext context) {
    List<Library> libraries = new ArrayList<Library>();
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries()));
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable(context.getProject()).getLibraries()));
    return libraries;
  }

  @NotNull
  public LibraryPackagingElement createEmpty(@NotNull Project project) {
    return new LibraryPackagingElement();
  }
}
