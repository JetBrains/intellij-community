/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.dnd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
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

public class FileCopyPasteUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.dnd.FileCopyPasteUtil");

  private FileCopyPasteUtil() { }

  public static DataFlavor createDataFlavor(@NotNull final String mimeType) {
    return createDataFlavor(mimeType, null, false);
  }

  public static DataFlavor createDataFlavor(@NotNull final String mimeType, @Nullable final Class<?> klass) {
    return createDataFlavor(mimeType, klass, false);
  }

  public static DataFlavor createDataFlavor(@NotNull final String mimeType, @Nullable final Class<?> klass, final boolean register) {
    try {
      final DataFlavor flavor =
        klass != null ? new DataFlavor(mimeType + ";class=" + klass.getName(), null, klass.getClassLoader()) : new DataFlavor(mimeType);

      if (register) {
        final FlavorMap map = SystemFlavorMap.getDefaultFlavorMap();
        if (map instanceof SystemFlavorMap) {
          ((SystemFlavorMap)map).addUnencodedNativeForFlavor(flavor, mimeType);
        }
      }

      return flavor;
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
      //noinspection ConstantConditions
      return null;
    }
  }

  public static DataFlavor createJvmDataFlavor(@NotNull final Class<?> klass) {
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

  public static boolean isFileListFlavorAvailable(@NotNull DataFlavor[] transferFlavors) {
    for (DataFlavor flavor : transferFlavors) {
      if (flavor != null && (flavor.equals(DataFlavor.javaFileListFlavor) ||
                             flavor.equals(LinuxDragAndDropSupport.uriListFlavor) ||
                             flavor.equals(LinuxDragAndDropSupport.gnomeFileListFlavor))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static List<File> getFileList(@NotNull final Transferable transferable) {
    try {
      if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @SuppressWarnings({"unchecked"})
        List<File> fileList = (List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);
        return fileList == null ? null : ContainerUtil.filter(fileList, file -> !StringUtil.isEmptyOrSpaces(file.getPath()));
      }
      else {
        return LinuxDragAndDropSupport.getFiles(transferable);
      }
    }
    catch (Exception ignore) { }

    return null;
  }

  @NotNull
  public static List<File> getFileListFromAttachedObject(Object attached) {
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
    return result == null? Collections.<File>emptyList() : result;
  }

  @NotNull
  public static List<VirtualFile> getVirtualFileListFromAttachedObject(Object attached) {
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
