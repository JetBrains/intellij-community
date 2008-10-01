package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class LocalFileStorage {
  private File myStorageIODirectory;

  public LocalFileStorage() {
    myStorageIODirectory = new File(PathManager.getSystemPath(), "httpFileSystem");
    myStorageIODirectory.mkdirs();
  }

  public File createLocalFile(@NotNull String url) throws IOException {
    int ast = url.indexOf('?');
    if (ast != -1) {
      url = url.substring(0, ast);
    }
    int last = url.lastIndexOf('/');
    String baseName;
    if (last == url.length() - 1) {
      baseName = url.substring(url.lastIndexOf('/', last-1) + 1, last);
    }
    else {
      baseName = url.substring(last + 1);
    }

    int index = baseName.lastIndexOf('.');
    String prefix = index == -1 ? baseName : baseName.substring(0, index);
    String suffix = index == -1 ? "" : baseName.substring(index+1);
    prefix = PathUtil.suggestFileName(prefix);
    suffix = PathUtil.suggestFileName(suffix);
    File file = FileUtil.findSequentNonexistentFile(myStorageIODirectory, prefix, suffix);
    file.createNewFile();
    return file;
  }

  public void deleteDownloadedFiles() {
    FileUtil.delete(myStorageIODirectory);
  }
}
