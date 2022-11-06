// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.rotateSelection

import com.intellij.openapi.editor.actionSystem.EditorAction

class RotateSelectionsForwardAction : EditorAction(RotateSelectionsHandler(false))

class RotateSelectionsBackwardsAction : EditorAction(RotateSelectionsHandler(true))

