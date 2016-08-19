/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.ide.dnd.FileCopyPasteUtil.createDataFlavor;

/**
 * Contains logic for supporting drag and drop in Linux.
 *
 * @author Christian Ihle
 */
public class LinuxDragAndDropSupport {
  public static final DataFlavor uriListFlavor = createDataFlavor("text/uri-list", String.class);
  public static final DataFlavor gnomeFileListFlavor = createDataFlavor("x-special/gnome-copied-files", null, true);
  public static final DataFlavor kdeCutMarkFlavor = createDataFlavor("application/x-kde-cutselection", null, true);

  private LinuxDragAndDropSupport() { }

  @Nullable
  public static List<File> getFiles(@NotNull final Transferable transferable) throws IOException, UnsupportedFlavorException {
    if (transferable.isDataFlavorSupported(uriListFlavor)) {
      final Object transferData = transferable.getTransferData(uriListFlavor);
      return getFiles(transferData.toString());
    }
    else if (transferable.isDataFlavorSupported(gnomeFileListFlavor)) {
      final Object transferData = transferable.getTransferData(gnomeFileListFlavor);
      final String content = FileUtil.loadTextAndClose((InputStream)transferData);
      return getFiles(content);
    }

    return null;
  }

  @NotNull
  private static List<File> getFiles(@Nullable final String transferData) {
    final List<File> fileList = new ArrayList<>();

    if (transferData != null) {
      final String[] uriList = StringUtil.convertLineSeparators(transferData).split("\n");
      for (String uriString : uriList) {
        if (StringUtil.isEmptyOrSpaces(uriString) || uriString.startsWith("#") || !uriString.startsWith("file:/")) {
          continue;
        }
        try {
          final URI uri = new URI(uriString);
          fileList.add(new File(uri));
        }
        catch (final URISyntaxException ignore) { }
      }
    }

    return fileList;
  }

  @NotNull
  public static String toUriList(@NotNull final List<File> files) {
    return StringUtil.join(files, file -> file.toURI().toString(), "\n");
  }

  public static boolean isMoveOperation(@NotNull final Transferable transferable) {
    if (transferable.isDataFlavorSupported(gnomeFileListFlavor)) {
      try {
        final Object transferData = transferable.getTransferData(gnomeFileListFlavor);
        final String content = FileUtil.loadTextAndClose((InputStream)transferData);
        return content.startsWith("cut\n");
      }
      catch (Exception ignored) { }
    }
    else if (transferable.isDataFlavorSupported(kdeCutMarkFlavor)) {
      return true;
    }

    return false;
  }
}
