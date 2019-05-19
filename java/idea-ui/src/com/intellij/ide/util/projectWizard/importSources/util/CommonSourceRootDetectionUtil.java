// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard.importSources.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;

public abstract class CommonSourceRootDetectionUtil<F> {

  protected CommonSourceRootDetectionUtil() {
  }

  @Nullable
  public Pair<F, String> suggestRootForFileWithPackageStatement(F file,
                                                                F topmostPossibleRoot,
                                                                NullableFunction<? super CharSequence, String> packageNameFetcher,
                                                                boolean packagePrefixSupported) {
    if (!isFile(file)) return null;

    final CharSequence chars;
    try {
      chars = loadText(file);
    }
    catch (IOException e) {
      return null;
    }

    String packageName = packageNameFetcher.fun(chars);
    if (packageName != null) {
      F root = getParentFile(file);
      int index = packageName.length();
      while (index > 0) {
        int index1 = packageName.lastIndexOf('.', index - 1);
        String token = packageName.substring(index1 + 1, index);
        String dirName = getName(root);
        final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
        if (!equalsToToken || root.equals(topmostPossibleRoot)) {
          String packagePrefix = packageName.substring(0, index);
          if (!packagePrefixSupported && packagePrefix.length() > 0) {
            return null;
          }
          return Pair.create(root, packagePrefix);
        }
        root = getParentFile(root);
        if (root == null) {
          return null;
        }
        index = index1;
      }
      return Pair.create(root, "");
    }

    return null;
  }

  protected abstract String getName(final F file);

  @Nullable
  protected abstract F getParentFile(final F file);

  protected abstract CharSequence loadText(final F file) throws IOException;

  protected abstract boolean isFile(final F file);

  public static final CommonSourceRootDetectionUtil<File> IO_FILE = new CommonSourceRootDetectionUtil<File>() {

    @Override
    protected String getName(final File file) {
      return file.getName();
    }

    @Override
    protected File getParentFile(final File file) {
      return file.getParentFile();
    }

    @Override
    protected CharSequence loadText(final File file) throws IOException {
      return StringFactory.createShared(loadFileTextSkippingBom(file));
    }

    @Override
    protected boolean isFile(final File file) {
      return file.isFile();
    }
  };

  private static char[] loadFileTextSkippingBom(File file) throws IOException {
    try (InputStream stream = CharsetToolkit.inputStreamSkippingBOM(new BufferedInputStream(new FileInputStream(file)));
         Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return FileUtilRt.loadText(reader, (int)file.length());
    }
  }

  public static final CommonSourceRootDetectionUtil<VirtualFile> VIRTUAL_FILE = new CommonSourceRootDetectionUtil<VirtualFile>() {

    @Override
    protected String getName(VirtualFile file) {
      return file.getName();
    }

    @Override
    protected VirtualFile getParentFile(final VirtualFile file) {
      return file.getParent();
    }

    @Override
    protected CharSequence loadText(final VirtualFile file) throws IOException {
      return VfsUtilCore.loadText(file);
    }

    @Override
    protected boolean isFile(final VirtualFile file) {
      return !file.isDirectory();
    }
  };

}
