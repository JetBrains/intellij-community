// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.preview;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Possible result for IntentionPreview.
 * @see com.intellij.codeInsight.intention.IntentionAction#generatePreview(Project, Editor, PsiFile)
 * @see com.intellij.codeInspection.LocalQuickFix#generatePreview(Project, ProblemDescriptor)
 */
@ApiStatus.NonExtendable
public interface IntentionPreviewInfo {
  /**
   * No intention preview is available.
   */
  IntentionPreviewInfo EMPTY = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  /**
   * Try to use fallback mechanism for intention preview instead.
   * Do not use this directly
   */
  @ApiStatus.Internal
  IntentionPreviewInfo FALLBACK_DIFF = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "FALLBACK";
    }
  };

  /**
   * Changes in the file copy should be displayed as intention preview
   */
  IntentionPreviewInfo DIFF = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "DIFF";
    }
  };

  /**
   * Diff preview where old text and new text are explicitly displayed.
   * Could be used to generate fake diff previews (e.g. when changes are to be applied to another file)
   */
  class CustomDiff implements IntentionPreviewInfo {
    private final @NotNull FileType myFileType;
    private final @NotNull String myOrigText;
    private final @NotNull String myModifiedText;

    /**
     * @param type file type, used for highlighting
     * @param origText original file text
     * @param modifiedText changed file text
     */
    public CustomDiff(@NotNull FileType type, @NotNull String origText, @NotNull String modifiedText) {
      myFileType = type;
      myOrigText = origText;
      myModifiedText = modifiedText;
    }

    public @NotNull FileType fileType() {
      return myFileType;
    }

    public @NotNull String originalText() {
      return myOrigText;
    }

    public @NotNull String modifiedText() {
      return myModifiedText;
    }
  }

  /**
   * HTML description
   */
  class Html implements IntentionPreviewInfo {
    private final @NotNull HtmlChunk myContent;
    private final @NotNull Map<String, Icon> myIconMap;

    /**
     * Construct description from HtmlChunk
     *
     * @param content description content
     */
    public Html(@NotNull HtmlChunk content) {
      this(content, Map.of());
    }

    /**
     * Construct description from raw HTML string
     *
     * @param contentHtml raw HTML content
     */
    public Html(@Nls @NotNull String contentHtml) {
      this(HtmlChunk.raw(contentHtml));
    }

    /**
     * Construct description from HtmlChunk and icon map
     *
     * @param content content that may refer to some icons via {@code &lt;icon src="$id$"&gt;}
     * @param iconMap a map from icon ID used in URL to the icon itself
     */
    private Html(@NotNull HtmlChunk content, @NotNull Map<String, Icon> iconMap) {
      myContent = content;
      myIconMap = Map.copyOf(iconMap);
    }

    /**
     * @return HTML content
     */
    public @NotNull HtmlChunk content() {
      return myContent;
    }

    /**
     * @param id icon ID
     * @return an icon referenced from HTML content via {@code &lt;icon src="$id$"&gt;}
     */
    @ApiStatus.Experimental
    public @Nullable Icon icon(@NotNull String id) {
      return myIconMap.get(id);
    }
  }

  /**
   * @param file    file to be renamed
   * @param newName new file name (with extension)
   * @return a preview that visualizes the file rename
   */
  static @NotNull IntentionPreviewInfo rename(@NotNull PsiFile file, @NotNull @NlsSafe String newName) {
    Icon icon = file.getIcon(0);
    HtmlChunk iconChunk =
      icon == null ? HtmlChunk.empty() :
      new HtmlBuilder().append(HtmlChunk.tag("icon").attr("src", "file")).nbsp().toFragment();
    HtmlChunk fragment = new HtmlBuilder()
      .append(iconChunk)
      .append(file.getName())
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(iconChunk)
      .append(newName)
      .toFragment();
    return new Html(fragment, icon == null ? Map.of() : Map.of("file", icon));
  }
}
