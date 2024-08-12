// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;

import java.util.*;

import static com.intellij.codeInsight.documentation.render.InlineDocumentationImplKt.inlineDocumentationItems;
import static com.intellij.lang.documentation.DocumentationMarkup.CLASS_SECTIONS;

public final class DocRenderPassFactory implements TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory, DumbAware {
  private static final Key<Long> MODIFICATION_STAMP = Key.create("doc.render.modification.stamp");
  private static final Key<Boolean> RESET_TO_DEFAULT = Key.create("doc.render.reset.to.default");
  private static final Key<Boolean> ICONS_ENABLED = Key.create("doc.render.icons.enabled");

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false);
  }

  @Override
  public @Nullable TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    long current = PsiModificationTracker.getInstance(file.getProject()).getModificationCount();
    boolean iconsEnabled = DocRenderDummyLineMarkerProvider.isGutterIconEnabled();
    Long existing = editor.getUserData(MODIFICATION_STAMP);
    Boolean iconsWereEnabled = editor.getUserData(ICONS_ENABLED);
    return editor.getProject() == null ||
           existing != null && existing == current && iconsWereEnabled != null && iconsWereEnabled == iconsEnabled
           ? null : new DocRenderPass(editor, file);
  }

  static void forceRefreshOnNextPass(@NotNull Editor editor) {
    editor.putUserData(MODIFICATION_STAMP, null);
    editor.putUserData(RESET_TO_DEFAULT, Boolean.TRUE);
  }

  private static final class DocRenderPass extends EditorBoundHighlightingPass implements DumbAware {
    private volatile Items items;

    DocRenderPass(@NotNull Editor editor, @NotNull PsiFile psiFile) {
      super(editor, psiFile, false);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      items = calculateItemsToRender(myEditor, myFile);
    }

    @Override
    public void doApplyInformationToEditor() {
      boolean resetToDefault = myEditor.getUserData(RESET_TO_DEFAULT) != null;
      myEditor.putUserData(RESET_TO_DEFAULT, null);
      applyItemsToRender(myEditor, myProject, items, resetToDefault && DocRenderManager.isDocRenderingEnabled(myEditor));
    }
  }

  public static @NotNull Items calculateItemsToRender(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    boolean enabled = DocRenderManager.isDocRenderingEnabled(editor);
    return calculateItemsToRender(editor.getDocument(), psiFile, enabled);
  }

  static @NotNull Items calculateItemsToRender(@NotNull Document document, @NotNull PsiFile psiFile, boolean enabled) {
    Items items = new Items();
    for (InlineDocumentation documentation : inlineDocumentationItems(psiFile)) {
      TextRange range = documentation.getDocumentationRange();
      if (isValidRange(document, range)) {
        String textToRender = enabled ? calcText(documentation) : null;
        items.addItem(new Item(range, textToRender));
      }
    }
    return items;
  }

  static boolean isValidRange(@NotNull Document document, @NotNull TextRange range) {
    CharSequence text = document.getImmutableCharSequence();
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    int startLine = document.getLineNumber(startOffset);
    int endLine = document.getLineNumber(endOffset);
    if (!CharArrayUtil.containsOnlyWhiteSpaces(text.subSequence(document.getLineStartOffset(startLine), startOffset)) ||
        !CharArrayUtil.containsOnlyWhiteSpaces(text.subSequence(endOffset, document.getLineEndOffset(endLine)))) {
      return false;
    }
    return startLine < endLine || document.getLineStartOffset(startLine) < document.getLineEndOffset(endLine);
  }

  static @NotNull @Nls String calcText(@Nullable InlineDocumentation documentation) {
    try {
      String text = documentation == null ? null : documentation.renderText();
      return text == null ? CodeInsightBundle.message("doc.render.not.available.text") : preProcess(text);
    }
    catch (IndexNotReadyException e) {
      return CodeInsightBundle.message("doc.render.dumb.mode.text");
    }
  }

  private static @NlsSafe String preProcess(@Nls String text) {
    var document = Jsoup.parse(text);
    DocumentationHtmlUtil.removeEmptySections$intellij_platform_lang_impl(document);
    DocumentationHtmlUtil.addParagraphsIfNeeded$intellij_platform_lang_impl(
      document, "table." + CLASS_SECTIONS + " td[valign=top]");
    DocumentationHtmlUtil.addExternalLinkIcons$intellij_platform_lang_impl(document);
    document.outputSettings().prettyPrint(false);
    return document.html();
  }

  public static void applyItemsToRender(@NotNull Editor editor,
                                        @NotNull Project project,
                                        @NotNull Items items,
                                        boolean collapseNewRegions) {
    editor.putUserData(MODIFICATION_STAMP, PsiModificationTracker.getInstance(project).getModificationCount());
    editor.putUserData(ICONS_ENABLED, DocRenderDummyLineMarkerProvider.isGutterIconEnabled());
    DocRenderItemManager.getInstance().setItemsToEditor(editor, items, collapseNewRegions);
  }

  public static final class Items implements Iterable<Item> {
    private final Map<TextRange, Item> myItems = new LinkedHashMap<>();
    private final boolean isZombie;

    public Items() {
      this(Collections.emptyList(), false);
    }

    Items(@NotNull Collection<@NotNull Item> items, boolean zombie) {
      isZombie = zombie;
      for (Item item : items) {
        addItem(item);
      }
    }

    public boolean isEmpty() {
      return myItems.isEmpty();
    }

    private void addItem(@NotNull Item item) {
      myItems.put(item.textRange, item);
    }

    @Nullable
    Item removeItem(@NotNull Segment textRange) {
      return myItems.remove(TextRange.create(textRange));
    }

    @Override
    public @NotNull Iterator<Item> iterator() {
      return myItems.values().iterator();
    }

    boolean isZombie() {
      return isZombie;
    }
  }

  public static final class Item {
    public final TextRange textRange;
    public final @Nls String textToRender;

    public Item(@NotNull TextRange textRange, @Nullable @Nls String textToRender) {
      this.textRange = textRange;
      this.textToRender = textToRender;
    }
  }
}
