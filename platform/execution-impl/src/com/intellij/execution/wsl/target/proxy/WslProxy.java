// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.proxy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class WslProxy {

  private static final Logger LOG = Logger.getInstance(WslProxy.class);
  private static final String PROXY_NAME = "wsl2_proxy.py";

  public static @NotNull File createPyFile() throws IOException {
    VirtualFile file = findWslProxyFile();
    String content = VfsUtilCore.loadText(file);
    File tempFile = FileUtil.createTempFile("intellij-wsl-proxy", ".py");
    FileUtil.writeToFile(tempFile, StringUtil.join(content, LineSeparator.LF.getSeparatorString()));
    return tempFile;
  }

  private static @NotNull VirtualFile findWslProxyFile() throws IOException {
    URL url = WslProxy.class.getResource(PROXY_NAME);
    if (url == null) {
      throw new IOException("Possibly broken installation: cannot find " + PROXY_NAME);
    }
    String fileUrl = VfsUtilCore.convertFromUrl(url);
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
    if (file != null && file.isValid()) {
      return file;
    }
    LOG.info("Cannot find virtual file for " + fileUrl + ", refreshing");
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          refreshByUrl(fileUrl);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      });
      throw new IOException("Cannot refresh " + fileUrl + " under read action");
    }
    else {
      file = refreshByUrl(fileUrl);
    }
    return file;
  }

  private static @NotNull VirtualFile refreshByUrl(@NotNull String fileUrl) throws IOException {
    VirtualFile file = VirtualFileManager.getInstance().refreshAndFindFileByUrl(fileUrl);
    if (file == null || !file.isValid()) {
      throw new IOException("Possibly broken installation: cannot find " + fileUrl + " after refresh");
    }
    return file;
  }
}
