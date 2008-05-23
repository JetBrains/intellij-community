package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author mike
 */
public class StorageUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.StorageUtil");

  private StorageUtil() {
  }

  static void save(final IFile file, final byte[] text) throws StateStorage.StateStorageException {
    final String filePath = file.getCanonicalPath();
    try {
      final Ref<IOException> refIOException = Ref.create(null);

      if (file.exists()) {
        final byte[] bytes = file.loadBytes();
        if (Arrays.equals(bytes, text)) return;
        IFile backupFile = deleteBackup(filePath);
        file.renameTo(backupFile);
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (!file.exists()) {
            file.createParentDirs();
          }

          try {
            getOrCreateVirtualFile(file, file).setBinaryContent(text);
          }
          catch (IOException e) {
            refIOException.set(e);
          }

          deleteBackup(filePath);
        }
      });
      if (refIOException.get() != null) {
        throw new StateStorage.StateStorageException(refIOException.get());
      }
    }
    catch (StateStorage.StateStorageException e) {
      throw new StateStorage.StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static IFile deleteBackup(final String path) {
    IFile backupFile = FileSystem.FILE_SYSTEM.createFile(path + "~");
    if (backupFile.exists()) {
      backupFile.delete();
    }
    return backupFile;
  }

  static VirtualFile getOrCreateVirtualFile(Object requestor, IFile ioFile) throws IOException {
    VirtualFile vFile = getVirtualFile(ioFile);

    if (vFile == null) {
      vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    }

    if (vFile == null) {
      final IFile parentFile = ioFile.getParentFile();
      final VirtualFile parentVFile =
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentFile); // need refresh if the directory has just been created
      if (parentVFile == null) {
        throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile.getPath()));
      }
      vFile = parentVFile.createChildData(requestor, ioFile.getName());
    }

    return vFile;
  }

  @Nullable
  static VirtualFile getVirtualFile(final IFile ioFile) {
    return LocalFileSystem.getInstance().findFileByIoFile(ioFile);
  }

  public static byte[] printDocument(final Document document) throws StateStorage.StateStorageException {
    try {
      return JDOMUtil.writeDocument(document, SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static byte[] printElement(Element element) throws StateStorage.StateStorageException {
    try {
      return JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static void save(IFile file, Element element) throws StateStorage.StateStorageException {
    try {
      save(file, JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8));
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }
}
