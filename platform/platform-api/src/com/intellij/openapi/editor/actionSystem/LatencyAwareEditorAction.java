// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

/**
 * Marker interface for editor actions the execution of which should be included in the typing latency report
 * (Tools | Internal Actions | Typing Latency Report).
 */
public interface LatencyAwareEditorAction {
}
