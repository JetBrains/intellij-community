package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileDrop {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.ex.FileDrop");

  public FileDrop(JComponent c, final Target target) {
    final DropTargetListener listener = new DropTargetListener() {

      public void dragEnter(final DropTargetDragEvent dtde) {
      }

      public void dragOver(final DropTargetDragEvent dtde) {
      }

      public void dropActionChanged(final DropTargetDragEvent dtde) {
      }

      public void dragExit(final DropTargetEvent dte) {
      }

      public void drop(final DropTargetDropEvent dtde) {
        dtde.acceptDrop(dtde.getDropAction());
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        try {
          final List list = (List)dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
          for (int i = 0; i < list.size(); i++) {
            Object each = list.get(i);
            if (each instanceof File) {
              final File eachFile = (File)each;
              final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(eachFile);
              if (vFile != null && vFile.exists()) {
                if (target.getDescriptor().isFileVisible(vFile, target.isHiddenShown())) {
                  files.add(vFile);
                }
              }
            }
          }
        }
        catch (Exception e) {
          LOG.debug(e);
        }
        if (files.size() > 0) {
          target.dropFiles(files);
        }
      }
    };

    new DropTarget(c, TransferHandler.COPY_OR_MOVE, listener, true);
  }

  public static interface Target {
    FileChooserDescriptor getDescriptor();
    boolean isHiddenShown();
    void dropFiles(List<VirtualFile> files);
  }

}
