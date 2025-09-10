// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/**
 * Some of our IDEs (Rider & CLion NOVA) use the model under which the usage infos for previews might arrive from the backend once
 * and then never get updated when the file gets edited (for performance & architectural reasons). The solution to have always valid
 * usage ranges is to juggle a highlighter around and update the *start* and *end* position of the usage according to the surrounding edits.
 */
@ApiStatus.Internal
public interface StickyUsageInfoOnRangeMarker {
  void bindToRangeMarker(RangeMarker rangeMarker);
  void rememberRanges();
}
