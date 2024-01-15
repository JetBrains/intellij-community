// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.preview;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.DeferredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

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
   * Changes in the file copy should be displayed as intention preview. Differences in leading/trailing 
   * whitespaces will be shown only if there are no other differences. Otherwise, whitespaces will be trimmed before shown the diff.
   */
  IntentionPreviewInfo DIFF = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "DIFF";
    }
  };

  /**
   * Changes in the file copy should be displayed as intention preview, without trimming any whitespaces.
   */
  IntentionPreviewInfo DIFF_NO_TRIM = new IntentionPreviewInfo() {
    @Override
    public String toString() {
      return "DIFF_NO_TRIM";
    }
  };

  /**
   * Diff preview for multiple files. UI may show only some of them if there are too many.
   */
  final class MultiFileDiff implements IntentionPreviewInfo {
    private final @NotNull List<@NotNull CustomDiff> myDiffs;

    public MultiFileDiff(@NotNull List<@NotNull CustomDiff> diffs) {
      myDiffs = diffs;
    }

    /**
     * @return list of individual CustomDiff objects to display
     */
    public @NotNull List<@NotNull CustomDiff> getDiffs() {
      return myDiffs;
    }
  }
  
  /**
   * Diff preview where original text and new text are explicitly specified.
   * Could be used to generate custom diff previews (e.g. when changes are to be applied to another file).
   * <p>
   * In most of the cases, original text could be empty, so simply the new text will be displayed.
   * However, sometimes you may provide carefully crafted original and new text, in order to get some diff highlighting
   * (added/removed parts).
   */
  final class CustomDiff implements IntentionPreviewInfo {
    private final @NotNull FileType myFileType;
    private final @NotNull String myOrigText;
    private final @NotNull String myModifiedText;
    private final @Nullable String myFileName;
    private final boolean myLineNumbers;

    /**
     * Construct a custom diff. Please prefer another constructor and specify a file name if it's applicable.
     *
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
      this(type, name, origText, modifiedText, false);
    }

    /**
     * @param type         file type, used for highlighting
     * @param name         file name, can be displayed to user if specified
     * @param origText     original file text
     * @param modifiedText changed file text
     * @param lineNumbers  if true then diff will display line numbers
     */
    public CustomDiff(@NotNull FileType type,
                      @Nullable String name,
                      @NotNull String origText,
                      @NotNull String modifiedText,
                      boolean lineNumbers) {
      myFileType = type;
      myFileName = name;
      myOrigText = origText;
      myModifiedText = modifiedText;
      myLineNumbers = lineNumbers;
    }

    public boolean showLineNumbers() {
      return myLineNumbers;
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
   * Kind of information displayed in HTML preview
   */
  enum InfoKind {
    /**
     * Informational preview (default)
     */
    INFORMATION,
    /**
     * Error
     */
    ERROR
  }

  /**
   * HTML description. Here are some advices:
   * <ul>
   *   <li>If you want to display icon, use {@link HtmlChunk#icon(String, Icon)}. Though be careful, as converting it to text
   *   and back (e.g., via {@link HtmlChunk#raw(String)}) would make the icon invalid. In general, avoid using {@link HtmlChunk#raw(String)}.
   *   Consider using {@link HtmlChunk#template(String, Map.Entry[])} if necessary</li>
   *   <li>Note that the description pane has fixed width (300 px for default font, see
   *   {@link com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor#MIN_WIDTH IntentionPreviewPopupUpdateProcessor#MIN_WIDTH}).
   *   Try to make the description not very long to avoid vertical scrollbar, as it's hardly possible to scroll it.</li>
   *   <li>In general, any user interaction with the description pane is hardly possible, as if you move the mouse it’s likely
   *   that preview for another item will be displayed. Also, preview is mostly useful for keyboard users, as it’s usually
   *   displayed as a reaction on Alt+Enter. So adding clickable links or buttons there is not a good idea.</li>
   *   <li>When possible, use generic preview methods available in {@link IntentionPreviewInfo} (e.g., {@link #rename(PsiFile, String)},
   *   {@link #navigate(NavigatablePsiElement)}, {@link #movePsi(PsiNamedElement, PsiNamedElement)}, {@link #moveMultiplePsi(List, PsiNamedElement)}). They provide uniform
   *   preview for common cases. Ask if you think that you need a new common method.</li>
   * </ul>
   */
  final class Html implements IntentionPreviewInfo {
    private final @NotNull HtmlChunk myContent;
    private final @NotNull InfoKind myInfoKind;

    /**
     * Construct description from HtmlChunk
     *
     * @param content description content
     */
    public Html(@NotNull HtmlChunk content) {
      this(content, InfoKind.INFORMATION);
    }

    /**
     * Construct description from HtmlChunk
     *
     * @param content  description content
     * @param infoKind kind of description box (may affect visual representation)
     */
    public Html(@NotNull HtmlChunk content, @NotNull InfoKind infoKind) {
      myContent = content;
      myInfoKind = infoKind;
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
     * @return HTML content
     */
    public @NotNull HtmlChunk content() {
      return myContent;
    }

    public @NotNull InfoKind infoKind() {
      return myInfoKind;
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
    return new Html(fragment.wrapWith("p"));
  }

  private static @NotNull HtmlChunk getIconChunk(@Nullable Icon icon, @NotNull String id) {
    if (icon instanceof DeferredIcon) {
      icon = ((DeferredIcon)icon).evaluate();
    }
    return icon == null ? HtmlChunk.empty() : new HtmlBuilder().append(HtmlChunk.icon(id, icon)).nbsp().toFragment();
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
    HtmlBuilder builder = new HtmlBuilder()
      .append(HtmlChunk.icon("file", fileIcon))
      .nbsp()
      .append(file.getName())
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(HtmlChunk.icon("dir", dirIcon))
      .nbsp()
      .append(location);
    return new Html(builder.wrapWith("p"));
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
    HtmlChunk moveFragment = getHtmlMoveFragment(sourceIcon, targetIcon, source.getName(), target.getName());
    return new Html(moveFragment.wrapWith("p"));
  }

  /**
   * @param sources List of PsiElements to move
   * @param target  target PsiElement
   * @return a presentation describing moving of source elements to the target
   */
  static @NotNull IntentionPreviewInfo moveMultiplePsi(
    @NotNull List<PsiNamedElement> sources,
    @NotNull PsiNamedElement target) {
    return moveMultiplePsi(sources, target, null);
  }

  /**
   * @param sources            List of PsiElements to move
   * @param target             target PsiElement
   * @param explicitTargetName Explicit name of the target element to be displayed; if null {@code target.getName()} will be used instead
   * @return a presentation describing moving of source elements to the target
   */
  static @NotNull IntentionPreviewInfo moveMultiplePsi(
    @NotNull List<PsiNamedElement> sources,
    @NotNull PsiNamedElement target,
    @Nullable @Nls String explicitTargetName) {

    HtmlBuilder builder = new HtmlBuilder();

    Icon targetIcon = getIcon(target);
    builder.appendWithSeparators(
      HtmlChunk.br(),
      ContainerUtil.map(sources, source -> getHtmlMoveFragment(
        getIcon(source),
        targetIcon,
        source.getName(),
        explicitTargetName == null ? target.getName() : explicitTargetName)));

    return new Html(builder.wrapWith("p"));
  }

  private static Icon getIcon(@NotNull PsiNamedElement source) {
    Icon icon = source.getIcon(0);
    if (icon instanceof DeferredIcon) {
      icon = ((DeferredIcon)icon).evaluate();
    }
    return icon;
  }

  private static @NotNull HtmlChunk getHtmlMoveFragment(@Nullable Icon sourceIcon,
                                                        @Nullable Icon targetIcon,
                                                        @Nullable @Nls String sourceName,
                                                        @Nullable @Nls String targetName) {
    return new HtmlBuilder()
      .append(getIconChunk(sourceIcon, "source_" + sourceName))
      .append(Objects.requireNonNull(sourceName))
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(getIconChunk(targetIcon, "target_" + targetName))
      .append(Objects.requireNonNull(targetName))
      .toFragment();
  }

  /**
   * @param target target element to navigate to
   * @return a presentation describing that the action will navigate to the specified target element
   */
  static @NotNull IntentionPreviewInfo navigate(@NotNull NavigatablePsiElement target) {
    PsiFile file = target.getContainingFile();
    int offset = target.getTextOffset();
    return navigate(file, offset);
  }

  /**
   * @param file file to navigate to
   * @param offset offset within file to navigate to
   * @return a presentation describing that the action will navigate to the specified target element
   */
  static @NotNull Html navigate(@NotNull PsiFile file, int offset) {
    Icon icon = file.getIcon(0);
    Document document = file.getFileDocument();
    HtmlBuilder builder = new HtmlBuilder();
    builder.append(HtmlChunk.htmlEntity("&rarr;")).append(" ");
    builder.append(getIconChunk(icon, "icon"));
    builder.append(file.getName());
    if (document.getTextLength() > offset) {
      int lineNumber = document.getLineNumber(offset);
      builder.append(AnalysisBundle.message("html.preview.navigate.line"))
        .append(String.valueOf(lineNumber+1));
    }
    return new Html(builder.wrapWith("p"));
  }

  /**
   * @param updatedList list after updating (containing new option)
   * @param addedOption an option added to the list
   * @param title a title text for the list
   * @return a presentation describing that the action will add the specified option to the options list
   */
  static IntentionPreviewInfo addListOption(@NotNull List<@NlsSafe String> updatedList,
                                            @NotNull String addedOption,
                                            @NotNull @Nls String title) {
    return addListOption(updatedList, title, Predicate.isEqual(addedOption));
  }

  /**
   * @param updatedList list after updating (containing new option)
   * @param title       a title text for the list
   * @param toSelect    predicate, which returns true if the option should be selected in preview
   * @return a presentation describing that the action will add the specified option to the options list
   */
  static IntentionPreviewInfo.Html addListOption(@NotNull List<@NlsSafe String> updatedList,
                                            @NotNull @Nls String title,
                                            @NotNull Predicate<String> toSelect) {
    int maxToList = Math.min(7, updatedList.size() + 2);
    HtmlChunk select = HtmlChunk.tag("select").attr("multiple", "multiple").attr("size", maxToList)
      .children(ContainerUtil.map2Array(updatedList, HtmlChunk.class, pref -> {
        HtmlChunk.Element chunk = HtmlChunk.tag("option").addText(pref);
        return toSelect.test(pref) ? chunk.attr("selected", "selected") : chunk;
      }));
    HtmlChunk content = new HtmlBuilder().append(title)
      .br().br()
      .append(select)
      .toFragment();
    return new Html(content);
  }
}
