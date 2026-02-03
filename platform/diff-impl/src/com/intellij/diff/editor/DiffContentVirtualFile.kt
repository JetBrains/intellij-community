// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

/**
 * An interface to mark virtual files which are used to display diff content.
 * Can be used in any place to distinguish diff [com.intellij.openapi.vfs.VirtualFile] from different inheritance hierarchies.
 */
interface DiffContentVirtualFile