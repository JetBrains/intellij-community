package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.impl.ui.properties.ArchiveElementPropertiesPanel;
import com.intellij.packaging.impl.ui.properties.DirectoryElementPropertiesPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class PackagingElementFactoryImpl extends PackagingElementFactory {
  public static final DirectoryElementType DIRECTORY_ELEMENT_TYPE = new DirectoryElementType();
  public static final ArchiveElementType ARCHIVE_ELEMENT_TYPE = new ArchiveElementType();
  public static final FileCopyElementType FILE_COPY_ELEMENT_TYPE = new FileCopyElementType();
  public static final ArtifactRootElementType ARTIFACT_ROOT_ELEMENT_TYPE = new ArtifactRootElementType();
  private static final PackagingElementType[] STANDARD_TYPES = {
      DIRECTORY_ELEMENT_TYPE, ARCHIVE_ELEMENT_TYPE,
      LibraryElementType.LIBRARY_ELEMENT_TYPE, ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE,
      ModuleWithDependenciesElementType.MODULE_WITH_DEPENDENCIES_TYPE, 
      ArtifactElementType.ARTIFACT_ELEMENT_TYPE, FILE_COPY_ELEMENT_TYPE,
  };

  @NotNull
  @Override
  public PackagingElementType<?>[] getNonCompositeElementTypes() {
    final List<PackagingElementType> elementTypes = new ArrayList<PackagingElementType>();
    for (PackagingElementType elementType : getAllElementTypes()) {
      if (!(elementType instanceof CompositePackagingElementType)) {
        elementTypes.add(elementType);
      }
    }
    return elementTypes.toArray(new PackagingElementType[elementTypes.size()]);
  }

  @NotNull
  @Override
  public CompositePackagingElementType<?>[] getCompositeElementTypes() {
    final List<CompositePackagingElementType> elementTypes = new ArrayList<CompositePackagingElementType>();
    for (PackagingElementType elementType : getAllElementTypes()) {
      if (elementType instanceof CompositePackagingElementType) {
        elementTypes.add((CompositePackagingElementType)elementType);
      }
    }
    return elementTypes.toArray(new CompositePackagingElementType[elementTypes.size()]);
  }

  @Override
  public PackagingElementType<?> findElementType(String id) {
    for (PackagingElementType elementType : getAllElementTypes()) {
      if (elementType.getId().equals(id)) {
        return elementType;
      }
    }
    if (id.equals(ARTIFACT_ROOT_ELEMENT_TYPE.getId())) {
      return ARTIFACT_ROOT_ELEMENT_TYPE;
    }
    throw new AssertionError(id + " not registered");
  }

  @NotNull
  @Override
  public PackagingElementType[] getAllElementTypes() {
    final PackagingElementType[] types = Extensions.getExtensions(PackagingElementType.EP_NAME);
    return ArrayUtil.mergeArrays(STANDARD_TYPES, types, PackagingElementType.class);
  }

  @NotNull
  @Override
  public PackagingElement<?> createArtifactElement(@NotNull Artifact artifact, @NotNull Project project) {
    return new ArtifactPackagingElement(project, ArtifactPointerManager.getInstance(project).create(artifact));
  }

  @NotNull
  public DirectoryPackagingElement createDirectory(@NotNull @NonNls String directoryName) {
    return new DirectoryPackagingElement(directoryName);
  }

  @NotNull
  @Override
  public ArtifactRootElement<?> createArtifactRootElement() {
    return new ArtifactRootElementImpl();
  }

  @Override
  @NotNull
  public CompositePackagingElement<?> getOrCreateDirectory(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath) {
    return getOrCreateDirectoryOrArchive(parent, relativePath, true);
  }

  @NotNull
  @Override
  public CompositePackagingElement<?> getOrCreateArchive(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath) {
    return getOrCreateDirectoryOrArchive(parent, relativePath, false);
  }

  @Override
  public void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull String outputDirectoryPath, @NotNull String sourceFilePath) {
    getOrCreateDirectory(root, outputDirectoryPath).addOrFindChild(new FileCopyPackagingElement(sourceFilePath));
  }

  @NotNull
  private CompositePackagingElement<?> getOrCreateDirectoryOrArchive(@NotNull CompositePackagingElement<?> root,
                                                                     @NotNull @NonNls String path, final boolean directory) {
    path = StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/");
    if (path.length() == 0) {
      return root;
    }
    int index = path.lastIndexOf('/');
    String lastName = path.substring(index + 1);
    String parentPath = index != -1 ? path.substring(0, index) : "";

    final CompositePackagingElement<?> parent = getOrCreateDirectoryOrArchive(root, parentPath, true);
    final CompositePackagingElement<?> last = directory ? createDirectory(lastName) : createArchive(lastName);
    return parent.addOrFindChild(last);
  }

  @NotNull
  public PackagingElement<?> createModuleOutput(@NotNull String moduleName, Project project) {
    return new ModuleOutputPackagingElement(moduleName);
  }

  @NotNull
  @Override
  public PackagingElement<?> createModuleOutput(@NotNull Module module) {
    return new ModuleOutputPackagingElement(module.getName());
  }

  @NotNull
  @Override
  public List<? extends PackagingElement<?>> createLibraryElements(@NotNull Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      return Collections.singletonList(createLibraryFiles(table.getTableLevel(), library.getName()));
    }
    final List<PackagingElement<?>> elements = new ArrayList<PackagingElement<?>>();
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      elements.add(new FileCopyPackagingElement(FileUtil.toSystemIndependentName(PathUtil.getLocalPath(file))));
    }
    return elements;
  }

  @NotNull
  @Override
  public PackagingElement<?> createLibraryFiles(@NotNull String level, @NotNull String name) {
    return new LibraryPackagingElement(level, name);
  }

  @NotNull
  public CompositePackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName) {
    return new ArchivePackagingElement(archiveFileName);
  }

  @Nullable
  private static PackagingElement<?> findArchiveOrDirectoryByName(@NotNull CompositePackagingElement<?> parent, @NotNull String name) {
    for (PackagingElement<?> element : parent.getChildren()) {
      if (element instanceof ArchivePackagingElement && ((ArchivePackagingElement)element).getArchiveFileName().equals(name) ||
          element instanceof DirectoryPackagingElement && ((DirectoryPackagingElement)element).getDirectoryName().equals(name)) {
        return element;
      }
    }
    return null;
  }

  @NotNull
  private static String suggestFileName(@NotNull CompositePackagingElement<?> parent, @NonNls @NotNull String prefix, @NonNls @NotNull String suffix) {
    String name = prefix + suffix;
    int i = 2;
    while (findArchiveOrDirectoryByName(parent, name) != null) {
      name = prefix + i++ + suffix;
    }
    return name;
  }

  @Override
  @NotNull
  public FileCopyPackagingElement createFileCopy(@NotNull String filePath) {
    return new FileCopyPackagingElement(filePath);
  }

  @NotNull
  @Override
  public PackagingElement<?> createFileCopyWithParentDirectories(@NotNull String filePath, @NotNull String relativeOutputPath) {
    return createFileCopyWithParentDirectories(filePath, relativeOutputPath, null);
  }

  @NotNull
  @Override
  public PackagingElement<?> createFileCopyWithParentDirectories(@NotNull String filePath,
                                                                 @NotNull String relativeOutputPath,
                                                                 @Nullable String outputFileName) {
    final FileCopyPackagingElement file = new FileCopyPackagingElement(filePath, outputFileName);
    return createParentDirectories(relativeOutputPath, file);
  }

  @NotNull
  @Override
  public PackagingElement<?> createParentDirectories(@NotNull String relativeOutputPath, @NotNull PackagingElement<?> element) {
    return createParentDirectories(relativeOutputPath, Collections.singletonList(element)).get(0);
  }

  @NotNull
  @Override
  public List<? extends PackagingElement<?>> createParentDirectories(@NotNull String relativeOutputPath, @NotNull List<? extends PackagingElement<?>> elements) {
    relativeOutputPath = StringUtil.trimStart(relativeOutputPath, "/");
    if (relativeOutputPath.length() == 0) {
      return elements;
    }
    int slash = relativeOutputPath.indexOf('/');
    if (slash == -1) slash = relativeOutputPath.length();
    String rootName = relativeOutputPath.substring(0, slash);
    String pathTail = relativeOutputPath.substring(slash);
    final DirectoryPackagingElement root = createDirectory(rootName);
    final CompositePackagingElement<?> last = getOrCreateDirectory(root, pathTail);
    last.addOrFindChildren(elements);
    return Collections.singletonList(root);
  }

  private static class DirectoryElementType extends CompositePackagingElementType<DirectoryPackagingElement> {
    private static final Icon ICON = IconLoader.getIcon("/actions/newFolder.png");

    private DirectoryElementType() {
      super("directory", CompilerBundle.message("element.type.name.directory"));
    }

    @Override
    public Icon getCreateElementIcon() {
      return ICON;
    }

    @NotNull
    public DirectoryPackagingElement createEmpty(@NotNull Project project) {
      return new DirectoryPackagingElement();
    }

    @Override
    public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull DirectoryPackagingElement element,
                                                                                                   @NotNull ArtifactEditorContext context) {
      if (ArtifactUtil.isArchiveName(element.getDirectoryName())) {
        return new DirectoryElementPropertiesPanel(element, context);
      }
      return null;
    }

    public DirectoryPackagingElement createComposite(@NotNull PackagingEditorContext context, CompositePackagingElement<?> parent) {
      final String initialValue = suggestFileName(parent, "folder", "");
      final String name = Messages.showInputDialog(context.getProject(), "Enter directory name: ", "New Directory", null, initialValue, null);
      if (name == null) return null;
      return new DirectoryPackagingElement(name);
    }
  }

  private static class ArchiveElementType extends CompositePackagingElementType<ArchivePackagingElement> {
    private ArchiveElementType() {
      super("archive", CompilerBundle.message("element.type.name.archive"));
    }

    @Override
    public Icon getCreateElementIcon() {
      return Icons.JAR_ICON;
    }

    @NotNull
    @Override
    public ArchivePackagingElement createEmpty(@NotNull Project project) {
      return new ArchivePackagingElement();
    }

    @Override
    public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull ArchivePackagingElement element,
                                                                                                 @NotNull ArtifactEditorContext context) {
      final String name = element.getArchiveFileName();
      if (name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar")) {
        return new ArchiveElementPropertiesPanel(element, context);
      }
      return null;
    }

    public ArchivePackagingElement createComposite(@NotNull PackagingEditorContext context, CompositePackagingElement<?> parent) {
      final String initialValue = suggestFileName(parent, "archive", ".jar");
      final String name = Messages.showInputDialog(context.getProject(), "Enter archive name: ", "New Archive", null, initialValue, null);
      if (name == null) return null;
      return new ArchivePackagingElement(name);
    }
  }

  private static class FileCopyElementType extends PackagingElementType<FileCopyPackagingElement> {
    private FileCopyElementType() {
      super("file-copy", "File");
    }

    @Override
    public Icon getCreateElementIcon() {
      return null;
    }

    @Override
    public boolean canCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact) {
      return true;
    }

    @NotNull
    public List<? extends FileCopyPackagingElement> chooseAndCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact,
                                                                     @NotNull CompositePackagingElement<?> parent) {
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, true);
      final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, context.getProject());
      final VirtualFile[] files = chooser.choose(null, context.getProject());
      final List<FileCopyPackagingElement> list = new ArrayList<FileCopyPackagingElement>();
      for (VirtualFile file : files) {
        list.add(new FileCopyPackagingElement(file.getPath()));
      }
      return list;
    }

    @NotNull
    public FileCopyPackagingElement createEmpty(@NotNull Project project) {
      return new FileCopyPackagingElement();
    }
  }

  private static class ArtifactRootElementType extends PackagingElementType<ArtifactRootElement<?>> {
    protected ArtifactRootElementType() {
      super("root", "");
    }

    @Override
    public boolean canCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact) {
      return false;
    }

    @NotNull
    public List<? extends ArtifactRootElement<?>> chooseAndCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact,
                                                                   @NotNull CompositePackagingElement<?> parent) {
      throw new UnsupportedOperationException("'create' not implemented in " + getClass().getName());
    }

    @NotNull
    public ArtifactRootElement<?> createEmpty(@NotNull Project project) {
      return new ArtifactRootElementImpl();
    }
  }
}
