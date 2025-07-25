// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;

public abstract class CommonSourceRootDetectionUtil<F> {
  protected CommonSourceRootDetectionUtil() { }

  public @Nullable Pair<F, String> suggestRootForFileWithPackageStatement(F file,
                                                                          F topmostPossibleRoot,
                                                                          NullableFunction<? super CharSequence, String> packageNameFetcher,
                                                                          boolean packagePrefixSupported) {
    if (!isFile(file)) return null;

    CharSequence chars;
    try {
      chars = loadText(file);
    }
    catch (IOException e) {
      return null;
    }

    String packageName = packageNameFetcher.fun(chars);
    if (packageName == null) return null;

    F root = getParentFile(file);
    if (root == null) return null;

    int index = packageName.length();
    while (index > 0) {
      int nextIndex = packageName.lastIndexOf('.', index - 1);
      String token = packageName.substring(nextIndex + 1, index);
      String dirName = getName(root);

      boolean equalsToToken = isCaseSensitive(root) ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
      if (!equalsToToken || root.equals(topmostPossibleRoot)) {
        return !packagePrefixSupported ? null : new Pair<>(root, packageName.substring(0, index));
      }

      root = getParentFile(root);
      if (root == null) {
        return null;
      }

      index = nextIndex;
    }

    return new Pair<>(root, "");
  }

  protected abstract String getName(F file);

  protected abstract boolean isCaseSensitive(@NotNull F file);

  protected abstract @Nullable F getParentFile(F file);

  protected abstract CharSequence loadText(F file) throws IOException;

  protected abstract boolean isFile(F file);

  public static final CommonSourceRootDetectionUtil<File> IO_FILE = new CommonSourceRootDetectionUtil<>() {
    @Override
    protected String getName(File file) {
      return file.getName();
    }

    @Override
    protected boolean isCaseSensitive(@NotNull File file) {
      FileAttributes.CaseSensitivity sensitivity = FileSystemUtil.readParentCaseSensitivity(file);
      return sensitivity.toBooleanWithDefault(SystemInfo.isFileSystemCaseSensitive);
    }

    @Override
    protected File getParentFile(File file) {
      return file.getParentFile();
    }

    @Override
    protected CharSequence loadText(File file) throws IOException {
      try (InputStream stream = CharsetToolkit.inputStreamSkippingBOM(new BufferedInputStream(new FileInputStream(file)));
           Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return StreamUtil.readText(reader);
      }
    }

    @Override
    protected boolean isFile(File file) {
      return file.isFile();
    }
  };

  public static final CommonSourceRootDetectionUtil<VirtualFile> VIRTUAL_FILE = new CommonSourceRootDetectionUtil<>() {
    @Override
    protected String getName(VirtualFile file) {
      return file.getName();
    }

    @Override
    protected boolean isCaseSensitive(@NotNull VirtualFile file) {
      VirtualFile parent = file.getParent();
      return parent != null ? parent.isCaseSensitive() : SystemInfo.isFileSystemCaseSensitive;
    }

    @Override
    protected VirtualFile getParentFile(VirtualFile file) {
      return file.getParent();
    }

    @Override
    protected CharSequence loadText(VirtualFile file) throws IOException {
      return VfsUtilCore.loadText(file);
    }

    @Override
    protected boolean isFile(VirtualFile file) {
      return !file.isDirectory();
    }
  };
}
