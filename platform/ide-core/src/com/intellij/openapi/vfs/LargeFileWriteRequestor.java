// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

/**
 * A marker interface for {@link VirtualFile#getOutputStream(Object)} to not assert file content size.
 * @see com.intellij.openapi.util.io.FileUtilRt#isTooLarge
 */
public interface LargeFileWriteRequestor {
}
