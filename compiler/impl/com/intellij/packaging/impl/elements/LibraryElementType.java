package com.intellij.packaging.impl.elements;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop.LibrariesSourceItemsProvider;
import com.intellij.openapi.roots.ui.configuration.packaging.ChooseLibrariesDialog;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.PackagingDragAndDropSourceItemsProvider;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
* @author nik
*/
public class LibraryElementType extends PackagingElementType<LibraryPackagingElement> {
  public static final LibraryElementType LIBRARY_ELEMENT_TYPE = new LibraryElementType();

  LibraryElementType() {
    super("library", "Library Files");
  }

  @Override
  public Icon getCreateElementIcon() {
    return Icons.LIBRARY_ICON;
  }

  @Override
  public PackagingDragAndDropSourceItemsProvider getDragAndDropSourceItemsProvider() {
    return new LibrariesSourceItemsProvider();
  }

  public static List<? extends Library> getNotAddedLibraries(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact) {
    final HashSet<Library> libraries = new HashSet<Library>();
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries()));
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable(context.getProject()).getLibraries()));
    ArtifactUtil.processPackagingElements(artifact, LIBRARY_ELEMENT_TYPE, new Processor<LibraryPackagingElement>() {
      public boolean process(LibraryPackagingElement libraryPackagingElement) {
        libraries.remove(libraryPackagingElement.findLibrary(context));
        return true;
      }
    }, context, true);
    return new ArrayList<Library>(libraries);
  }

  @NotNull
  public List<? extends LibraryPackagingElement> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                  CompositePackagingElement<?> parent) {
    ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(context.getProject(), getNotAddedLibraries(context, artifact),
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

  @NotNull
  public LibraryPackagingElement createEmpty() {
    return new LibraryPackagingElement();
  }
}
