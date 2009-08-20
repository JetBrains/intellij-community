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

  public static List<? extends Library> getNotAddedLibraries(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact,
                                                             List<Library> librariesList) {
    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    ArtifactUtil.processPackagingElements(artifact, PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE, new Processor<FileCopyPackagingElement>() {
      public boolean process(FileCopyPackagingElement fileCopyPackagingElement) {
        final VirtualFile root = fileCopyPackagingElement.getLibraryRoot();
        if (root != null) {
          roots.add(root);
        }
        return true;
      }
    }, context, true);
    final List<Library> result = new ArrayList<Library>();
    for (Library library : librariesList) {
      if (!roots.containsAll(Arrays.asList(library.getFiles(OrderRootType.CLASSES)))) {
        result.add(library);
      }
    }
    return result;
  }

  @NotNull
  public List<? extends LibraryPackagingElement> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                  CompositePackagingElement<?> parent) {
    List<Library> libraries = new ArrayList<Library>();
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries()));
    libraries.addAll(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable(context.getProject()).getLibraries()));
    ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(context.getProject(), getNotAddedLibraries(context, artifact, libraries),
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
  public LibraryPackagingElement createEmpty(@NotNull Project project) {
    return new LibraryPackagingElement();
  }
}
