// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

/**
 * A marker interface for {@link VirtualFile#getOutputStream(Object)} to not assert file content size
 * and use an {@link com.intellij.util.io.PreemptiveSafeFileOutputStream alternative implementation} of safe file output stream.
 *
 * @see VirtualFileUtil#isTooLarge
 * @see com.intellij.util.io.PreemptiveSafeFileOutputStream
 */
public interface LargeFileWriteRequestor { }
