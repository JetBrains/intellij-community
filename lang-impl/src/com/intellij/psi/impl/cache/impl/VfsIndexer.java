package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiBundle;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class VfsIndexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.VfsIndexer");

  public static VirtualFile[] writeFileIndex(
      OutputStream stream,
      VirtualFile root,
      VirtualFileFilter filter) throws IOException {

    List<VirtualFile> result = new ArrayList<VirtualFile>();

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null){
      progress.setText2(PsiBundle.message("psi.scanning.files.in.folder.progress", root.getPresentableUrl()));
    }

    DataOutputStream out = stream == null ? null : new DataOutputStream(stream);

    _writeFileIndex(out, root, filter, result);

    if (out != null){
      out.flush();
    }

    return result.toArray(new VirtualFile[result.size()]);
  }

  private static void _writeFileIndex(DataOutputStream out, VirtualFile file, VirtualFileFilter filter, List<VirtualFile> result) throws IOException {
    ProgressManager.getInstance().checkCanceled();

    result.add(file);
    if (out != null){
      out.writeUTF(file.getName());
    }

    VirtualFile[] children = file.getChildren();
    if (children == null) {
      if (out != null){
        out.writeInt(0);
      }
      return;
    }

    int childrenCount = 0;
    for (VirtualFile child : children) {
      if (filter.accept(child)) childrenCount++;
    }

    if (out != null){
      out.writeInt(childrenCount);
    }
    for (VirtualFile child : children) {
      if (filter.accept(child)) {
        _writeFileIndex(out, child, filter, result);
      }
    }
  }

  public static VirtualFile[] readFileIndex(
      InputStream stream,
      VirtualFile root,
      VirtualFileFilter filter
      ) throws IOException {
    List<VirtualFile> result = new ArrayList<VirtualFile>();

    DataInputStream in = new DataInputStream(stream);

    String rootName = in.readUTF();
    LOG.assertTrue(root.getName().equals(rootName));
    _readFileIndex(in, root, filter, result);

    return result.toArray(new VirtualFile[result.size()]);
  }

  private static void _readFileIndex(DataInputStream in, VirtualFile file, VirtualFileFilter filter, List<VirtualFile> result) throws IOException {
    ProgressManager.getInstance().checkCanceled();

    result.add(file);
    int childrenCount = in.readInt();
    if (childrenCount == 0) return;

    for (int i = 0; i < childrenCount; i++) {
      String name = in.readUTF();
      VirtualFile child = file != null ? file.findChild(name) : null;

      if (child != null && !filter.accept(child)){
        child = null;
      }

      _readFileIndex(in, child, filter, result);
    }
  }
}
