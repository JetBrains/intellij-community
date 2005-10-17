/**
 * @author Yura Cangea
 */
package com.intellij.debugger.settings;

import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface DebuggerColors {
  TextAttributesKey BREAKPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("BREAKPOINT_ATTRIBUTES");
  TextAttributesKey EXECUTIONPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EXECUTIONPOINT_ATTRIBUTES");
}
