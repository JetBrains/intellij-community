// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.nio.file.Path;
import java.util.*;

public final class FileCopyPasteUtil {
  private static final Logger LOG = Logger.getInstance(FileCopyPasteUtil.class);
  private static final DataFlavor[] FLAVORS =
    {DataFlavor.javaFileListFlavor, LinuxDragAndDropSupport.uriListFlavor, LinuxDragAndDropSupport.gnomeFileListFlavor};

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
    return CopyPasteManager.getInstance().areDataFlavorsAvailable(FLAVORS);
  }

  public static boolean isFileListFlavorAvailable(@NotNull DnDEvent event) {
    return ContainerUtil.or(FLAVORS, f -> event.isDataFlavorSupported(f));
  }

  public static boolean isFileListFlavorAvailable(DataFlavor @NotNull [] transferFlavors) {
    var supported = Set.of(FLAVORS);
    return ContainerUtil.exists(transferFlavors, f -> f != null && supported.contains(f));
  }

  public static @Nullable List<File> getFileList(@NotNull Transferable transferable) {
    var files = getFiles(transferable);
    return files != null ? ContainerUtil.map(files, it -> it.toFile()) : null;
  }

  public static @Nullable List<Path> getFiles(@NotNull Transferable transferable) {
    try {
      if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @SuppressWarnings("unchecked") var files = (List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);
        return ContainerUtil.mapNotNull(files, file -> !Strings.isEmptyOrSpaces(file.getPath()) ? file.toPath() : null);
      }
      else {
        return LinuxDragAndDropSupport.getFiles(transferable);
      }
    }
    catch (Exception e) {
      LOG.debug(e);
    }

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
    return result == null ? List.of() : result;
  }

  public static @NotNull List<VirtualFile> getVirtualFileListFromAttachedObject(Object attached) {
    var files = getFileListFromAttachedObject(attached);
    if (files.isEmpty()) {
      return List.of();
    }
    else {
      var result = new ArrayList<VirtualFile>(files.size());
      for (File file : files) {
        var virtualFile = VfsUtil.findFileByIoFile(file, true);
        if (virtualFile == null) continue;
        result.add(virtualFile);
        // detect and store file type for Finder-2-IDEA drag-n-drop
        virtualFile.getFileType();
      }
      return result;
    }
  }
}
