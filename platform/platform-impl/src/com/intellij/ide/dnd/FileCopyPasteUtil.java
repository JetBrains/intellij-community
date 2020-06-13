// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FileCopyPasteUtil {
  private static final Logger LOG = Logger.getInstance(FileCopyPasteUtil.class);

  private FileCopyPasteUtil() { }

  public static DataFlavor createDataFlavor(@NotNull String mimeType) {
    return createDataFlavor(mimeType, null, false);
  }

  public static DataFlavor createDataFlavor(@NotNull String mimeType, @Nullable Class<?> klass) {
    return createDataFlavor(mimeType, klass, false);
  }

  public static DataFlavor createDataFlavor(@NotNull String mimeType, @Nullable Class<?> klass, boolean register) {
    try {
      DataFlavor flavor =
        klass != null ? new DataFlavor(mimeType + ";class=" + klass.getName(), null, klass.getClassLoader()) : new DataFlavor(mimeType);

      if (register) {
        FlavorMap map = SystemFlavorMap.getDefaultFlavorMap();
        if (map instanceof SystemFlavorMap) {
          ((SystemFlavorMap)map).addUnencodedNativeForFlavor(flavor, mimeType);
        }
      }

      return flavor;
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
      return null;
    }
  }

  public static DataFlavor createJvmDataFlavor(@NotNull Class<?> klass) {
    return createDataFlavor(DataFlavor.javaJVMLocalObjectMimeType, klass, false);
  }

  public static boolean isFileListFlavorAvailable() {
    return CopyPasteManager.getInstance().areDataFlavorsAvailable(
      DataFlavor.javaFileListFlavor, LinuxDragAndDropSupport.uriListFlavor, LinuxDragAndDropSupport.gnomeFileListFlavor
    );
  }

  public static boolean isFileListFlavorAvailable(@NotNull DnDEvent event) {
    return event.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
           event.isDataFlavorSupported(LinuxDragAndDropSupport.uriListFlavor) ||
           event.isDataFlavorSupported(LinuxDragAndDropSupport.gnomeFileListFlavor);
  }

  public static boolean isFileListFlavorAvailable(DataFlavor @NotNull [] transferFlavors) {
    for (DataFlavor flavor : transferFlavors) {
      if (flavor != null && (flavor.equals(DataFlavor.javaFileListFlavor) ||
                             flavor.equals(LinuxDragAndDropSupport.uriListFlavor) ||
                             flavor.equals(LinuxDragAndDropSupport.gnomeFileListFlavor))) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable List<File> getFileList(@NotNull Transferable transferable) {
    try {
      if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @SuppressWarnings("unchecked")
        List<File> fileList = (List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);
        return ContainerUtil.filter(fileList, file -> !Strings.isEmptyOrSpaces(file.getPath()));
      }
      else {
        return LinuxDragAndDropSupport.getFiles(transferable);
      }
    }
    catch (Exception ignore) { }

    return null;
  }

  public static @NotNull List<File> getFileListFromAttachedObject(Object attached) {
    List<File> result;
    if (attached instanceof TransferableWrapper) {
      result = ((TransferableWrapper)attached).asFileList();
    }
    else if (attached instanceof DnDNativeTarget.EventInfo) {
      result = getFileList(((DnDNativeTarget.EventInfo)attached).getTransferable());
    }
    else {
      result = null;
    }
    return result == null ? Collections.emptyList() : result;
  }

  public static @NotNull List<VirtualFile> getVirtualFileListFromAttachedObject(Object attached) {
    List<VirtualFile> result;
    List<File> fileList = getFileListFromAttachedObject(attached);
    if (fileList.isEmpty()) {
      result = Collections.emptyList();
    }
    else {
      result = new ArrayList<>(fileList.size());
      for (File file : fileList) {
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
        if (virtualFile == null) continue;
        result.add(virtualFile);
        // detect and store file type for Finder-2-IDEA drag-n-drop
        virtualFile.getFileType();
      }
    }
    return result;
  }
}