package com.intellij.codeHighlighting;

/**
 * @author yole
 */
public interface DirtyScopeTrackingHighlightingPassFactory extends TextEditorHighlightingPassFactory {
  int getPassId();
}
