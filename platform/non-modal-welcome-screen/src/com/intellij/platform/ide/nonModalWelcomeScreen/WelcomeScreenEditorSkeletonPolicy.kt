// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorSkeletonPolicy
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabVirtualFile

internal class WelcomeScreenEditorSkeletonPolicy : EditorSkeletonPolicy {
  override fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): Boolean =
    (fileEditorComposite as? EditorComposite)?.file !is WelcomeScreenRightTabVirtualFile
}
