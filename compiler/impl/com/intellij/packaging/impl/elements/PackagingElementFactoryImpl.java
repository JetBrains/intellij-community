package com.intellij.packaging.impl.elements;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
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
      ArtifactElementType.ARTIFACT_ELEMENT_TYPE, FILE_COPY_ELEMENT_TYPE,
  };

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

  @Override
  public PackagingElementType[] getAllElementTypes() {
    final PackagingElementType[] types = Extensions.getExtensions(PackagingElementType.EP_NAME);
    return ArrayUtil.mergeArrays(STANDARD_TYPES, types, PackagingElementType.class);
  }

  public DirectoryPackagingElement createDirectory(@NotNull @NonNls String directoryName) {
    return new DirectoryPackagingElement(directoryName);
  }

  @Nullable
  public CompositePackagingElement<?> createDirectories(@NotNull @NonNls String path) {
    path = StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/");
    if (path.length() == 0) return null;
    int index = path.lastIndexOf('/');
    String lastName = path.substring(index + 1);
    String parentPath = index != -1 ? path.substring(0, index-1) : "";
    final CompositePackagingElement<?> last = createDirectory(lastName);
    final CompositePackagingElement<?> parent = createDirectories(parentPath);
    if (parent != null) {
      parent.addChild(last);
      return parent;
    }
    return last;
  }

  public PackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName) {
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
  private static String suggestFileName(@NotNull CompositePackagingElement<?> parent, @NotNull String prefix, @NotNull String suffix) {
    String name = prefix + suffix;
    int i = 2;
    while (findArchiveOrDirectoryByName(parent, name) != null) {
      name = prefix + i++ + suffix;
    }
    return name;
  }

  @Override
  public PackagingElement<?> createFileCopy(@NotNull String filePath, @NotNull String relativeOutputPath) {
    final FileCopyPackagingElement file = new FileCopyPackagingElement(filePath);
    final CompositePackagingElement<?> parent = createDirectories(relativeOutputPath);
    if (parent != null) {
      parent.addChild(file);
      return parent;
    }
    return file;
  }

  private static class DirectoryElementType extends CompositePackagingElementType<DirectoryPackagingElement> {
    private static final Icon ICON = IconLoader.getIcon("/actions/newFolder.png");

    private DirectoryElementType() {
      super("directory", "Directory");
    }

    @Override
    public Icon getCreateElementIcon() {
      return ICON;
    }

    @NotNull
    public DirectoryPackagingElement createEmpty() {
      return new DirectoryPackagingElement();
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
      super("archive", "Archive");
    }

    @Override
    public Icon getCreateElementIcon() {
      return Icons.JAR_ICON;
    }

    @NotNull
    @Override
    public ArchivePackagingElement createEmpty() {
      return new ArchivePackagingElement();
    }

    @Override
    public PackagingElementPropertiesPanel<ArchivePackagingElement> createElementPropertiesPanel() {
      return new ArchiveElementPropertiesPanel();
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

    @NotNull
    public List<? extends FileCopyPackagingElement> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                     CompositePackagingElement<?> parent) {
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
    public FileCopyPackagingElement createEmpty() {
      return new FileCopyPackagingElement();
    }
  }

  private static class ArtifactRootElementType extends PackagingElementType<ArtifactRootElement<?>> {
    protected ArtifactRootElementType() {
      super("root", "Root");
    }

    @NotNull
    public List<? extends ArtifactRootElement<?>> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                   CompositePackagingElement<?> parent) {
      throw new UnsupportedOperationException("'create' not implemented in " + getClass().getName());
    }

    @NotNull
    public ArtifactRootElement<?> createEmpty() {
      return new ArtifactRootElementImpl();
    }
  }
}
