package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class ReadOnlyAttributeUtil {
  /**
   * Sets specified read-only status for the spcified <code>file</code>.
   * This method can be performed only for files which are in local file system.
   * @param file file which read-only attribute to be changed.
   * @param readOnlyStatus new read-only status.
   * @throws java.lang.IllegalArgumentException if passed <code>file</code> doesn't
   * belong to the local file system.
   * @throws IOException if some <code>IOExecption</code> occurred.
   */
  public static void setReadOnlyAttribute(VirtualFile file, boolean readOnlyStatus) throws IOException {
    if (!(file.getFileSystem() instanceof LocalFileSystem)) {
      throw new IllegalArgumentException("Wrong file system: "+file.getFileSystem());
    }
    if(file.isWritable()==!readOnlyStatus){
      return;
    }
    String path = file.getPresentableUrl();

    setReadOnlyAttribute(path, readOnlyStatus);
    file.refresh(false, false);
  }

  public static void setReadOnlyAttribute(String path, boolean readOnlyStatus) throws IOException {
    Process process;
    if(SystemInfo.isWindows){
      process=Runtime.getRuntime().exec(
        new String[]{
          "attrib",
          readOnlyStatus ? "+r" : "-r",
          path
        }
      );
    }else{ // UNIXes go here
      process=Runtime.getRuntime().exec(
        new String[]{
          "chmod",
          readOnlyStatus ? "u-w" : "u+w",
          path
        }
      );
    }
    try {
      process.waitFor();
    }catch (InterruptedException e) {
    }
  }
}
