// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class ScriptFileUtil {

  private static final Logger LOG = Logger.getInstance(ScriptFileUtil.class);

  private static final String SCHEME = "mem://";
  private static final Map<String, VirtualFile> ourFilesMap = ContainerUtil.createConcurrentWeakValueMap();
  private static final AtomicLong ourFileCounter = new AtomicLong();

  private ScriptFileUtil() {}

  public static boolean isMemoryScriptPath(@Nullable String url) {
    return url != null && url.startsWith(SCHEME);
  }

  public static String getScriptFilePath(@NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) return file.getPath();

    long id = ourFileCounter.incrementAndGet();
    String url = SCHEME + id + "/" + file.getName();
    ourFilesMap.put(url, file);
    return url;
  }

  @Nullable
  public static VirtualFile findScriptFileByPath(@Nullable String path) {
    if (StringUtil.isEmpty(path)) return null;
    if (!path.startsWith(SCHEME)) {
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
    return ourFilesMap.get(path);
  }

  @NotNull
  public static String getLocalFilePath(@NotNull String scriptPath) throws CantRunException {
    if (isMemoryScriptPath(scriptPath)) {
      File tmpFile = copyToTempFile(scriptPath);
      return tmpFile.getAbsolutePath();
    }
    return scriptPath;
  }

  @NotNull
  public static File copyToTempFile(@NotNull @NlsSafe String path) throws CantRunException {
    VirtualFile virtualFile = findScriptFileByPath(path);
    if (virtualFile == null) {
      throw new CantRunException(ExecutionBundle.message("script.exception.file.not.found", path));
    }
    File ioFile;
    try {
      ioFile = FileUtil.createTempFile(virtualFile.getName(), "", true);
    }
    catch (IOException e) {
      throw new CantRunException(ExecutionBundle.message("script.exception.can.not.create.temp.file", virtualFile.getName()), e);
    }
    try {
      copyFile(virtualFile, ioFile);
      return ioFile;
    }
    catch (IOException e) {
      throw new CantRunException(
        ExecutionBundle.message("script.exception.can.not.write.temp.file", virtualFile.getPath(), ioFile.getAbsolutePath()), e);
    }
  }

  private static void copyFile(@NotNull VirtualFile srcFile, @NotNull File destFile) throws IOException {
    LOG.info("Copying to " + destFile.getPath());
    CharSequence content = getContent(srcFile);
    CharBuffer cb = CharBuffer.wrap(content);
    ByteBuffer bb = StandardCharsets.UTF_8.encode(cb);
    byte[] result = new byte[bb.remaining()];
    bb.get(result);
    FileUtil.writeToFile(destFile, result, false);
  }

  @NotNull
  private static CharSequence getContent(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) {
      return document.getText();
    }
    return LoadTextUtil.loadText(file);
  }

}
