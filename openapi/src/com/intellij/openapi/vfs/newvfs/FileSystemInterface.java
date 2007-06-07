/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileSystemInterface {
  boolean exists(VirtualFile fileOrDirectory);

  String[] list(VirtualFile file);

  boolean isDirectory(VirtualFile file);

  long getTimeStamp(VirtualFile file);
  void setTimeStamp(VirtualFile file, long modstamp) throws IOException;

  boolean isWritable(VirtualFile file);
  void setWritable(VirtualFile file, boolean writableFlag) throws IOException;

  VirtualFile createChildDirectory(final Object requestor, VirtualFile parent, String dir) throws IOException;
  VirtualFile createChildFile(final Object requestor, VirtualFile parent, String file) throws IOException;

  void deleteFile(final Object requestor, VirtualFile file) throws IOException;
  void moveFile(final Object requestor, VirtualFile file, VirtualFile newParent) throws IOException;
  void renameFile(final Object requestor, VirtualFile file, String newName) throws IOException;
  VirtualFile copyFile(final Object requestor, VirtualFile file, VirtualFile newParent, final String copyName) throws IOException;

  InputStream getInputStream(VirtualFile file) throws IOException;
  OutputStream getOutputStream(VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException;

  /**
   * @return the CRC-32 checksum of the file content, or -1 if not known
   * @param file a file to get CRC-32 checksum for
   */
  long getCRC(VirtualFile file);

  long getLength(VirtualFile file);
}