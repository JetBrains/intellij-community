// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.codeInsight.documentation.render.InlineDocumentationImplKt.inlineDocumentationItems;

public final class DocRenderPassFactory implements TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory, DumbAware {
  private static final Key<Long> MODIFICATION_STAMP = Key.create("doc.render.modification.stamp");
  private static final Key<Long> FOLDING_OPERATION_MODIFICATION_STAMP = Key.create("doc.render.folding.modification.stamp");
  private static final Key<Boolean> RESET_TO_DEFAULT = Key.create("doc.render.reset.to.default");
  private static final Key<Boolean> ICONS_ENABLED = Key.create("doc.render.icons.enabled");

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false);
  }

  @Nullable
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    long current = PsiModificationTracker.getInstance(file.getProject()).getModificationCount();
    boolean iconsEnabled = DocRenderDummyLineMarkerProvider.isGutterIconEnabled();
    Long existing = editor.getUserData(MODIFICATION_STAMP);
    Long existingFoldingStamp = editor.getUserData(FOLDING_OPERATION_MODIFICATION_STAMP);
    Boolean iconsWereEnabled = editor.getUserData(ICONS_ENABLED);
    long existingFoldingModelCounter = FoldingModelImpl.getFoldingOperationCounter(editor);
    return editor.getProject() == null ||
           existing != null && existing == current &&
           existingFoldingStamp != null && existingFoldingStamp == existingFoldingModelCounter &&
           iconsWereEnabled != null && iconsWereEnabled == iconsEnabled
           ? null : new DocRenderPass(editor, file);
  }

  @ApiStatus.Internal
  public static void forceRefreshOnNextPass(@NotNull Editor editor) {
    editor.putUserData(MODIFICATION_STAMP, null);
    editor.putUserData(FOLDING_OPERATION_MODIFICATION_STAMP, null);
    editor.putUserData(RESET_TO_DEFAULT, Boolean.TRUE);
  }

  private static final class DocRenderPass extends EditorBoundHighlightingPass implements DumbAware {
    private volatile Items items;
    private volatile long foldingCounter;
    private volatile long psiCounter;

    DocRenderPass(@NotNull Editor editor, @NotNull PsiFile psiFile) {
      super(editor, psiFile, false);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      psiCounter = PsiModificationTracker.getInstance(myFile.getProject()).getModificationCount();
      foldingCounter = FoldingModelImpl.getFoldingOperationCounter(myEditor);
      items = calculateItemsToRender(myEditor, myFile);
    }

    @Override
    public void doApplyInformationToEditor() {
      boolean resetToDefault = myEditor.getUserData(RESET_TO_DEFAULT) != null;
      myEditor.putUserData(RESET_TO_DEFAULT, null);
      applyItemsToRender(myEditor, items, psiCounter, foldingCounter, resetToDefault && DocRenderManager.isDocRenderingEnabled(myEditor));
    }
  }

  @NotNull
  public static Items calculateItemsToRender(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    boolean enabled = DocRenderManager.isDocRenderingEnabled(editor);
    Items items = new Items();
    for (InlineDocumentation documentation : inlineDocumentationItems(psiFile)) {
      TextRange range = documentation.getDocumentationRange();
      if (isValidRange(editor, range)) {
        String textToRender = enabled ? calcText(documentation) : null;
        items.addItem(new Item(range, textToRender));
      }
    }
    return items;
  }

  static boolean isValidRange(@NotNull Editor editor, @NotNull TextRange range) {
    Document document = editor.getDocument();
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

  private static String preProcess(String text) {
    return DocumentationManager.addExternalLinksIcon(text);
  }

  public static void applyItemsToRender(@NotNull Editor editor,
                                        @NotNull Items items,
                                        long psiModificationStamp,
                                        long foldingModelStamp,
                                        boolean collapseNewRegions) {
    editor.putUserData(MODIFICATION_STAMP, psiModificationStamp);
    editor.putUserData(FOLDING_OPERATION_MODIFICATION_STAMP, foldingModelStamp);
    editor.putUserData(ICONS_ENABLED, DocRenderDummyLineMarkerProvider.isGutterIconEnabled());
    DocRenderItemManager.getInstance().setItemsToEditor(editor, items, collapseNewRegions);
  }

  /**
   * used in external plugin
   */
  @SuppressWarnings("unused")
  public static void applyItemsToRender(@NotNull Editor editor,
                                        Project project,
                                        @NotNull Items items,
                                        boolean collapseNewRegions) {
    applyItemsToRender(editor,
                       items,
                       PsiModificationTracker.getInstance(project).getModificationCount(),
                       FoldingModelImpl.getFoldingOperationCounter(editor),
                       collapseNewRegions);
  }

  public static final class Items implements Iterable<Item> {
    private final Map<TextRange, Item> myItems = new LinkedHashMap<>();

    boolean isEmpty() {
      return myItems.isEmpty();
    }

    private void addItem(@NotNull Item item) {
      myItems.put(item.textRange, item);
    }

    @Nullable
    Item removeItem(@NotNull Segment textRange) {
      return myItems.remove(TextRange.create(textRange));
    }

    @NotNull
    @Override
    public Iterator<Item> iterator() {
      return myItems.values().iterator();
    }
  }

  static final class Item {
    final TextRange textRange;
    final @Nls String textToRender;

    private Item(@NotNull TextRange textRange, @Nullable @Nls String textToRender) {
      this.textRange = textRange;
      this.textToRender = textToRender;
    }
  }
}
