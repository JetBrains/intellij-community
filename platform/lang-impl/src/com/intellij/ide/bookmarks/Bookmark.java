// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmarks;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

public final class Bookmark implements Navigatable, Comparable<Bookmark> {
  private OpenFileDescriptor myTarget;
  private Reference<RangeHighlighterEx> myHighlighterRef;

  // hold values only if uninitialized
  private int myLine;
  private String myUrl;

  private @NotNull @NlsSafe String myDescription;
  private char myMnemonic;
  int index; // index in the list of bookmarks in the Navigate|Bookmarks|show

  @ApiStatus.Internal
  public Bookmark(@NotNull String url, int line, @NotNull @NlsSafe String description) {
    myUrl = url;
    myLine = line;
    myDescription = description;
  }

  Bookmark(@NotNull Project project, @NotNull VirtualFile file, int line, @NotNull @NlsSafe String description) {
    myDescription = description;

    initTarget(project, file, line);
  }

  @Nullable
  OpenFileDescriptor init(@NotNull Project project) {
    if (myTarget != null) {
      throw new IllegalStateException("Bookmark is already initialized (file=" + myTarget + ")");
    }

    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(myUrl);
    if (file == null) {
      return null;
    }

    myUrl = null;
    initTarget(project, file, myLine);
    myLine = -1;
    return myTarget;
  }

  private void initTarget(@NotNull Project project, @NotNull VirtualFile file, int line) {
    myTarget = new OpenFileDescriptor(project, file, line, -1, true);
  }

  public static @NotNull Font getBookmarkFont() {
    return EditorFontType.getGlobalPlainFont();
  }

  @Override
  public int compareTo(@NotNull Bookmark o) {
    int i = myMnemonic != 0 ? o.myMnemonic != 0 ? myMnemonic - o.myMnemonic : -1: o.myMnemonic != 0 ? 1 : 0;
    if (i != 0) {
      return i;
    }

    i = myTarget.getProject().getName().compareTo(o.myTarget.getProject().getName());
    if (i != 0) {
      return i;
    }

    i = myTarget.getFile().getName().compareTo(o.getFile().getName());
    if (i != 0) {
      return i;
    }
    return getTarget().compareTo(o.getTarget());
  }

  void updateHighlighter() {
    release();
  }

  Document getCachedDocument() {
    return FileDocumentManager.getInstance().getCachedDocument(getFile());
  }

  public void release() {
    int line = getLine();
    if (line < 0) {
      return;
    }
    final Document document = getCachedDocument();
    if (document == null) return;
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myTarget.getProject(), true);
    final Document markupDocument = markup.getDocument();
    if (markupDocument.getLineCount() <= line) return;
    RangeHighlighterEx highlighter = findMyHighlighter();
    if (highlighter != null) {
      myHighlighterRef = null;
      highlighter.dispose();
    }
  }

  private RangeHighlighterEx findMyHighlighter() {
    final Document document = getCachedDocument();
    if (document == null) return null;
    RangeHighlighterEx result = SoftReference.dereference(myHighlighterRef);
    if (result != null) {
      return result;
    }
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myTarget.getProject(), true);
    final Document markupDocument = markup.getDocument();
    final int startOffset = 0;
    final int endOffset = markupDocument.getTextLength();

    final Ref<RangeHighlighterEx> found = new Ref<>();
    markup.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      GutterMark renderer = highlighter.getGutterIconRenderer();
      if (renderer instanceof MyGutterIconRenderer && ((MyGutterIconRenderer)renderer).myBookmark == this) {
        found.set(highlighter);
        return false;
      }
      return true;
    });
    result = found.get();
    myHighlighterRef = result == null ? null : new WeakReference<>(result);
    return result;
  }

  public Icon getIcon() {
    return getType().getIcon();
  }

  public @NotNull @NlsSafe String getDescription() {
    return myDescription;
  }

  public void setDescription(@NotNull @NlsSafe String description) {
    myDescription = description;
  }

  public BookmarkType getType() {
    return BookmarkType.get(getMnemonic());
  }

  public char getMnemonic() {
    return myMnemonic;
  }

  public void setMnemonic(char mnemonic) {
    myMnemonic = Character.toUpperCase(mnemonic);
  }

  public @NotNull VirtualFile getFile() {
    return myTarget.getFile();
  }

  @Nullable
  String nullizeEmptyDescription() {
    return StringUtil.nullize(myDescription);
  }

  public boolean isValid() {
    if (!getFile().isValid()) {
      return false;
    }
    if (getLine() ==-1) {
      return true;
    }
    RangeHighlighterEx highlighter = findMyHighlighter();
    return highlighter != null && highlighter.isValid();
  }

  @Override
  public boolean canNavigate() {
    return getTarget().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getTarget().canNavigateToSource();
  }

  @Override
  public void navigate(boolean requestFocus) {
    getTarget().navigate(requestFocus);
  }

  public int getLine() {
    int targetLine = myTarget.getLine();
    if (targetLine == -1) return -1;
    //What user sees in gutter
    RangeHighlighterEx highlighter = findMyHighlighter();
    if (highlighter != null && highlighter.isValid()) {
      Document document = highlighter.getDocument();
      return document.getLineNumber(highlighter.getStartOffset());
    }
    RangeMarker marker = myTarget.getRangeMarker();
    if (marker != null && marker.isValid()) {
      Document document = marker.getDocument();
      return document.getLineNumber(marker.getStartOffset());
    }
    return targetLine;
  }

  public boolean hasLine() {
    return myTarget.getLine() >= 0;
  }

  private @NotNull OpenFileDescriptor getTarget() {
    int line = getLine();
    if (line != myTarget.getLine()) {
      myTarget = new OpenFileDescriptor(myTarget.getProject(), myTarget.getFile(), line, -1, true);
    }
    return myTarget;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(myTarget == null ? myUrl : getQualifiedName());
    String text = nullizeEmptyDescription();
    String description = text == null ? null : StringUtil.escapeXmlEntities(text);
    if (description != null) {
      result.append(": ").append(description);
    }
    return result.toString();
  }

  public @NotNull String getQualifiedName() {
    String presentableUrl = myTarget.getFile().getPresentableUrl();
    if (myTarget.getFile().isDirectory()) {
      return presentableUrl;
    }

    final PsiFile psiFile = PsiManager.getInstance(myTarget.getProject()).findFile(myTarget.getFile());

    if (psiFile == null) return presentableUrl;

    StructureViewBuilder builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile);
    if (builder instanceof TreeBasedStructureViewBuilder) {
      StructureViewModel model = ((TreeBasedStructureViewBuilder)builder).createStructureViewModel(null);
      Object element;
      try {
        element = model.getCurrentEditorElement();
      }
      finally {
        Disposer.dispose(model);
      }
      if (element instanceof NavigationItem) {
        ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        if (presentation != null) {
          presentableUrl = ((NavigationItem)element).getName() + " " + presentation.getLocationString();
        }
      }
    }

    return BookmarkBundle.message("bookmark.file.X.line.Y", presentableUrl, getLine() + 1);
  }

  private @NotNull @NlsContexts.Tooltip String getBookmarkTooltip() {
    StringBuilder result = new StringBuilder(BookmarkBundle.message("bookmark.text"));
    if (myMnemonic != 0) {
      result.append(" ").append(myMnemonic);
    }
    String text = nullizeEmptyDescription();
    String description = text == null ? null : StringUtil.escapeXmlEntities(text);
    if (description != null) {
      result.append(": ").append(description);
    }

    StringBuilder shortcutDescription = new StringBuilder();
    if (myMnemonic != 0) {
      String shortcutToToggle = KeymapUtil.getFirstKeyboardShortcutText("ToggleBookmark" + myMnemonic);
      String shortcutToNavigate = KeymapUtil.getFirstKeyboardShortcutText("GotoBookmark" + myMnemonic);
      if (!shortcutToToggle.isEmpty()) {
        shortcutDescription.append(shortcutToNavigate.isEmpty()
                                   ? BookmarkBundle.message("bookmark.shortcut.to.toggle", shortcutToToggle)
                                   : BookmarkBundle.message("bookmark.shortcut.to.toggle.and.jump", shortcutToToggle, shortcutToNavigate));
      } else if (!shortcutToNavigate.isEmpty()){
        shortcutDescription.append(BookmarkBundle.message("bookmark.shortcut.to.jump", shortcutToNavigate));
      }
    }

    if (shortcutDescription.length() == 0) {
      String shortcutToToggle = KeymapUtil.getFirstKeyboardShortcutText("ToggleBookmark");
      if (shortcutToToggle.length() > 0) {
        shortcutDescription.append(BookmarkBundle.message("bookmark.shortcut.to.toggle", shortcutToToggle));
      }
    }

    if (shortcutDescription.length() > 0) {
      result.append(" (").append(shortcutDescription).append(")");
    }

    //noinspection HardCodedStringLiteral
    return result.toString();
  }

  private static final class MyGutterIconRenderer extends GutterIconRenderer implements DumbAware {
    private final Bookmark myBookmark;

    MyGutterIconRenderer(@NotNull Bookmark bookmark) {
      myBookmark = bookmark;
    }

    @Override
    public @NotNull Alignment getAlignment() {
      return Alignment.RIGHT;
    }

    @Override
    public @NotNull Icon getIcon() {
      return myBookmark.getType().getGutterIcon();
    }

    @Override
    public @NotNull String getTooltipText() {
      return myBookmark.getBookmarkTooltip();
    }

    @Override
    public @NotNull GutterDraggableObject getDraggableObject() {
      return new GutterDraggableObject() {
        @Override
        public boolean copy(int line, VirtualFile file, int actionId) {
          myBookmark.myTarget = new OpenFileDescriptor(myBookmark.myTarget.getProject(), file, line, -1, true);
          myBookmark.updateHighlighter();
          return true;
        }
      };
    }

    @Override
    public @NotNull String getAccessibleName() {
      return BookmarkBundle.message("accessible.name.icon.bookmark.0", myBookmark.myMnemonic);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyGutterIconRenderer &&
             Objects.equals(getTooltipText(), ((MyGutterIconRenderer)obj).getTooltipText()) &&
             Comparing.equal(getIcon(), ((MyGutterIconRenderer)obj).getIcon());
    }

    @Override
     public int hashCode() {
      return getIcon().hashCode();
    }

    @Override
    public @Nullable ActionGroup getPopupMenuActions() {
      return (ActionGroup)ActionManager.getInstance().getAction("popup@BookmarkContextMenu");
    }
  }

  @ApiStatus.Internal
  public static @NotNull @NlsSafe String toString(char mnemonic, boolean point) {
    StringBuilder sb = new StringBuilder().append(mnemonic);
    if (point) sb.append('.');
    return sb.toString(); //NON-NLS
  }
}
