// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// This is a bit of a crutch inherited from the Forefathers. Please refer to RiderGutterMarksPreprocessor if you have questions.
@ApiStatus.Internal
public interface MergeableGutterIconRendererProvider {

  ExtensionPointName<MergeableGutterIconRendererProvider> EP_NAME  = ExtensionPointName.create("com.intellij.mergeableGutterIconRendererProvider");

  @Nullable MergeableGutterIconRenderer tryGetMergeableIconRenderer(@NotNull GutterMark mark);
}
