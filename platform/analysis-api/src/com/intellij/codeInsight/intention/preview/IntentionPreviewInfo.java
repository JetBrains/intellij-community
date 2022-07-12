// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.preview;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.DeferredIcon;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
   * Changes in the file copy should be displayed as intention preview, without trimming any whitespaces
   */
  IntentionPreviewInfo DIFF_NO_TRIM = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "DIFF_NO_TRIM";
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
    private final @Nullable String myFileName;

    /**
     * @param type         file type, used for highlighting
     * @param origText     original file text
     * @param modifiedText changed file text
     */
    public CustomDiff(@NotNull FileType type, @NotNull String origText, @NotNull String modifiedText) {
      this(type, null, origText, modifiedText);
    }

    /**
     * @param type         file type, used for highlighting
     * @param name         file name, can be displayed to user if specified
     * @param origText     original file text
     * @param modifiedText changed file text
     */
    public CustomDiff(@NotNull FileType type, @Nullable String name, @NotNull String origText, @NotNull String modifiedText) {
      myFileType = type;
      myFileName = name;
      myOrigText = origText;
      myModifiedText = modifiedText;
    }

    public @Nullable String fileName() {
      return myFileName;
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
    public Html(@NotNull HtmlChunk content, @NotNull Map<String, Icon> iconMap) {
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
    HtmlChunk iconChunk = getIconChunk(icon, "file");
    HtmlChunk fragment = new HtmlBuilder()
      .append(iconChunk)
      .append(file.getName())
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(iconChunk)
      .append(newName)
      .toFragment();
    return new Html(fragment.wrapWith("p"), icon == null ? Map.of() : Map.of("file", icon));
  }

  @NotNull
  private static HtmlChunk getIconChunk(@Nullable Icon icon, @NotNull String id) {
    return icon == null ? HtmlChunk.empty() :
         new HtmlBuilder().append(HtmlChunk.tag("icon").attr("src", id)).nbsp().toFragment();
  }

  /**
   * @param file    file to be moved
   * @param directory target directory
   * @return a preview that visualizes the file move
   */
  static @NotNull IntentionPreviewInfo moveToDirectory(@NotNull VirtualFile file, @NotNull VirtualFile directory) {
    Icon fileIcon = IconUtil.getIcon(file, 0, null);
    Icon dirIcon = IconUtil.getIcon(directory, 0, null);
    @NlsSafe String location = VfsUtilCore.getRelativeLocation(directory, file.getParent());
    if (location == null) {
      location = directory.getPath();
    }
    HtmlChunk fragment = new HtmlBuilder()
      .append(HtmlChunk.tag("icon").attr("src", "file"))
      .nbsp()
      .append(file.getName())
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(HtmlChunk.tag("icon").attr("src", "dir"))
      .nbsp()
      .append(location)
      .toFragment();
    return new Html(fragment.wrapWith("p"), Map.of("file", fileIcon, "dir", dirIcon));
  }

  /**
   * @param source PsiElement to move
   * @param target target PsiElement
   * @return a presentation describing moving of source element to the target
   */
  static @NotNull IntentionPreviewInfo movePsi(@NotNull PsiNamedElement source, @NotNull PsiNamedElement target) {
    Icon sourceIcon = source.getIcon(0);
    if (sourceIcon instanceof DeferredIcon) {
      sourceIcon = ((DeferredIcon)sourceIcon).evaluate();
    }
    Icon targetIcon = target.getIcon(0);
    if (targetIcon instanceof DeferredIcon) {
      targetIcon = ((DeferredIcon)targetIcon).evaluate();
    }
    HtmlChunk sourceIconChunk = getIconChunk(sourceIcon, "source");
    HtmlChunk targetIconChunk = getIconChunk(targetIcon, "target");
    HtmlChunk fragment = new HtmlBuilder()
      .append(sourceIconChunk)
      .append(Objects.requireNonNull(source.getName()))
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(targetIconChunk)
      .append(Objects.requireNonNull(target.getName()))
      .toFragment();
    Map<String, Icon> iconMap = new HashMap<>();
    iconMap.put("source", sourceIcon);
    iconMap.put("target", targetIcon);
    return new Html(fragment.wrapWith("p"), iconMap);
  }
}
