// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Implement this interface to override encoding specified in {@link EncodingRegistry} for an arbitrary virtual file
 * and define an extension in {@code plugin.xml}, for example:
 * <pre><code>
 *   &lt;fileEncodingProvider implementation="com.acme.example.MyFileEncodingProvider"/&gt;
 * </code></pre>
 * <b>Note:</b> The provider doesn't affect files defining their own encoding via {@code LanguageFileType.getCharset()}.
 */
public interface FileEncodingProvider {
  ExtensionPointName<FileEncodingProvider> EP_NAME = new ExtensionPointName<>("com.intellij.fileEncodingProvider");

  /**
   * @param virtualFile The virtual file to override encoding for.
   * @return The encoding to be used for the given virtual file. <b>It should not depend on the current project. Otherwise it may
   * cause index inconsistencies.</b>
   */
  @Nullable
  Charset getEncoding(@NotNull VirtualFile virtualFile);

}
