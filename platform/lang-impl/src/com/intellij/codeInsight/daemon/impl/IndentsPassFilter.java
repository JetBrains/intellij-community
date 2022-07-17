package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Allows disabling the IJ-based indent guides pass and/or disabling specific indent guides in a file.
 * This can be useful if you are implementing your own custom indent guides and don't want IJ-based ones to get in the way.
 * Example: Rider disables IJ-based pass entirely for C# files, because R# already adds its own indent guides there.
 * Another example: Rider keeps IJ-based pass for Razor files, but removes indent guides that both start and end in a C# code block.
 */
public interface IndentsPassFilter {
  ExtensionPointName<IndentsPassFilter> EXTENSION_POINT = new ExtensionPointName<>("com.intellij.daemon.indentsPassFilter");

  boolean shouldRunIndentsPass(@NotNull Editor editor);

  boolean shouldShowIndentGuide(@NotNull Editor editor, @NotNull IndentGuideDescriptor descriptor);
}
