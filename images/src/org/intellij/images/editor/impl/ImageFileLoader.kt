// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile

internal interface ImageFileLoader : Disposable {

  fun loadFile(file: VirtualFile?)

}
