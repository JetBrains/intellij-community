// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.documentation.DocumentationTarget;
import com.intellij.platform.documentation.InlineDocumentation;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.*;

import static com.intellij.codeInsight.documentation.render.InlineDocumentationImplKt.findInlineDocumentation;

public final class DocRenderItemImpl implements DocRenderItem {
  private final Editor editor;
  private final RangeHighlighter highlighter;
  private @Nls String textToRender;
  private CustomFoldRegion foldRegion;

  @Nls
  @Nullable
  @Override
  public String getTextToRender() {
    return textToRender;
  }

  void setTextToRender(@Nls String text) {
    textToRender = text;
  }

  @Override
  public @Nullable CustomFoldRegion getFoldRegion() {
    return foldRegion;
  }

  @Override
  public @NotNull RangeHighlighter getHighlighter() {
    return highlighter;
  }

  @Override
  public @NotNull Editor getEditor() {
    return editor;
  }

  @Override
  public GutterIconRenderer calcGutterIconRenderer() {
    MyGutterIconRenderer highlighterIconRenderer =
      (MyGutterIconRenderer)highlighter.getGutterIconRenderer();
    return highlighterIconRenderer == null
           ? null
           : new MyGutterIconRenderer(AllIcons.Gutter.JavadocEdit,
                                      ((MyGutterIconRenderer)highlighter.getGutterIconRenderer()).isIconVisible());
  }

  public static CustomFoldRegionRenderer createDemoRenderer(@NotNull Editor editor) {
    DocRenderItemImpl item = new DocRenderItemImpl(editor, new TextRange(0, 0), CodeInsightBundle.message(
      "documentation.rendered.documentation.with.href.link"));
    return new DocRenderer(item);
  }

  DocRenderItemImpl(@NotNull Editor editor, @NotNull TextRange textRange, @Nullable @Nls String textToRender) {
    this.editor = editor;
    this.textToRender = textToRender;
    highlighter = editor.getMarkupModel()
      .addRangeHighlighter(null, textRange.getStartOffset(), textRange.getEndOffset(), 0, HighlighterTargetArea.EXACT_RANGE);
    updateIcon(null);
  }

  boolean isValid() {
    return highlighter.isValid() &&
           highlighter.getStartOffset() < highlighter.getEndOffset() &&
           new ItemLocation(highlighter).matches(foldRegion);
  }

  boolean remove(@NotNull Collection<Runnable> foldingTasks) {
    highlighter.dispose();
    if (foldRegion != null && foldRegion.isValid()) {
      foldingTasks.add(() -> foldRegion.getEditor().getFoldingModel().removeFoldRegion(foldRegion));
      return true;
    }
    return false;
  }

  @Override
  public void toggle() {
    toggle(null);
  }

  boolean toggle(@Nullable Collection<? super Runnable> foldingTasks) {
    if (!(editor instanceof EditorEx)) return false;
    FoldingModelEx foldingModel = ((EditorEx)editor).getFoldingModel();
    if (foldRegion == null) {
      if (textToRender == null && foldingTasks == null) {
        generateHtmlInBackgroundAndToggle();
        return false;
      }
      ItemLocation offsets = new ItemLocation(highlighter);
      Runnable foldingTask = () -> {
        foldRegion = foldingModel.addCustomLinesFolding(offsets.foldStartLine, offsets.foldEndLine, new DocRenderer(this));
      };
      if (foldingTasks == null) {
        foldingModel.runBatchFoldingOperation(foldingTask, true, false);
        foldRegion.update();
      }
      else {
        foldingTasks.add(foldingTask);
      }
    }
    else {
      Runnable foldingTask = () -> {
        int startOffset = foldRegion.getStartOffset();
        int endOffset = foldRegion.getEndOffset();
        foldingModel.removeFoldRegion(foldRegion);
        for (FoldRegion region : foldingModel.getRegionsOverlappingWith(startOffset, endOffset)) {
          if (region.getStartOffset() >= startOffset && region.getEndOffset() <= endOffset) {
            region.setExpanded(true);
          }
        }
        foldRegion = null;
      };
      if (foldingTasks == null) {
        foldingModel.runBatchFoldingOperation(foldingTask, true, false);
      }
      else {
        foldingTasks.add(foldingTask);
      }
      if (!DocRenderManager.isDocRenderingEnabled(editor)) {
        // the value won't be updated by DocRenderPass on document modification, so we shouldn't cache the value
        textToRender = null;
      }
    }
    return true;
  }

  private void generateHtmlInBackgroundAndToggle() {
    ReadAction.nonBlocking(() -> DocRenderPassFactory.calcText(getInlineDocumentation()))
      .withDocumentsCommitted(Objects.requireNonNull(editor.getProject()))
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.any(), (@Nls String html) -> {
        textToRender = html;
        toggle();
      }).submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public @Nullable InlineDocumentation getInlineDocumentation() {
    if (highlighter.isValid()) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(Objects.requireNonNull(editor.getProject()));
      PsiFile file = psiDocumentManager.getPsiFile(editor.getDocument());
      if (file != null) {
        return findInlineDocumentation(file, highlighter.getTextRange());
      }
    }
    return null;
  }

  @Override
  public @Nullable DocumentationTarget getInlineDocumentationTarget() {
    InlineDocumentation documentation = getInlineDocumentation();
    return documentation == null ? null : documentation.getOwnerTarget();
  }

  private static void updateRenderers(@NotNull Collection<DocRenderItem> items, boolean recreateContent) {
    DocRenderItemUpdater.getInstance().updateFoldRegions(ContainerUtil.mapNotNull(items, i -> i.foldRegion), recreateContent);
  }

  private static void updateRenderers(@NotNull Editor editor, boolean recreateContent) {
    if (recreateContent) {
      DocRenderer.clearCachedLoadingPane(editor);
    }
    Collection<DocRenderItem> items = editor.getUserData(OUR_ITEMS);
    if (items != null) updateRenderers(items, recreateContent);
  }

  void updateIcon(List<? super Runnable> foldingTasks) {
    boolean iconEnabled = DocRenderDummyLineMarkerProvider.isGutterIconEnabled();
    boolean iconExists = highlighter.getGutterIconRenderer() != null;
    if (iconEnabled != iconExists) {
      if (iconEnabled) {
        highlighter.setGutterIconRenderer(new MyGutterIconRenderer(AllIcons.Gutter.JavadocRead, false));
      }
      else {
        highlighter.setGutterIconRenderer(null);
      }
      if (foldRegion != null) {
        ((DocRenderer)foldRegion.getRenderer()).update(false, false, foldingTasks);
      }
    }
  }

  @Override
  public AnAction createToggleAction() {
    return new ToggleRenderingAction(this);
  }

  @Override
  public void setIconVisible(boolean visible) {
    MyGutterIconRenderer iconRenderer = (MyGutterIconRenderer)highlighter.getGutterIconRenderer();
    if (iconRenderer != null) {
      iconRenderer.setIconVisible(visible);
      int y = editor.visualLineToY(((EditorImpl)editor).offsetToVisualLine(highlighter.getStartOffset()));
      repaintGutter(y);
    }
    if (foldRegion != null) {
      MyGutterIconRenderer inlayIconRenderer = (MyGutterIconRenderer)foldRegion.getGutterIconRenderer();
      if (inlayIconRenderer != null) {
        inlayIconRenderer.setIconVisible(visible);
        repaintGutter(editor.offsetToXY(foldRegion.getStartOffset()).y);
      }
    }
  }

  private void repaintGutter(int startY) {
    JComponent gutter = (JComponent)editor.getGutter();
    gutter.repaint(0, startY, gutter.getWidth(), startY + editor.getLineHeight());
  }

  private static final class ItemLocation {
    private final int foldStartLine;
    private final int foldEndLine;

    private ItemLocation(@NotNull RangeHighlighter highlighter) {
      Document document = highlighter.getDocument();
      foldStartLine = document.getLineNumber(highlighter.getStartOffset());
      foldEndLine = document.getLineNumber(highlighter.getEndOffset());
    }

    private boolean matches(CustomFoldRegion foldRegion) {
      return foldRegion == null ||
             foldRegion.isValid() &&
             foldRegion.getStartOffset() == foldRegion.getEditor().getDocument().getLineStartOffset(foldStartLine) &&
             foldRegion.getEndOffset() == foldRegion.getEditor().getDocument().getLineEndOffset(foldEndLine);
    }
  }

  private class MyGutterIconRenderer extends GutterIconRenderer implements DumbAware {
    private final LayeredIcon icon;

    MyGutterIconRenderer(Icon icon, boolean iconVisible) {
      this.icon = new LayeredIcon(icon);
      setIconVisible(iconVisible);
    }

    boolean isIconVisible() {
      return icon.isLayerEnabled(0);
    }

    void setIconVisible(boolean visible) {
      icon.setLayerEnabled(0, visible);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyGutterIconRenderer;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return icon;
    }

    @Override
    public @NotNull String getAccessibleName() {
      return CodeInsightBundle.message("doc.render.icon.accessible.name");
    }

    @NotNull
    @Override
    public Alignment getAlignment() {
      return Alignment.RIGHT;
    }

    @Override
    public boolean isNavigateAction() {
      return true;
    }

    @Nullable
    @Override
    public String getTooltipText() {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC);
      if (action == null) return null;
      String actionText = action.getTemplateText();
      if (actionText == null) return null;
      return XmlStringUtil.wrapInHtml(actionText + HelpTooltip.getShortcutAsHtml(KeymapUtil.getFirstKeyboardShortcutText(action)));
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
      return createToggleAction();
    }

    @Override
    public ActionGroup getPopupMenuActions() {
      return ObjectUtils.tryCast(ActionManager.getInstance().getAction(IdeActions.GROUP_DOC_COMMENT_GUTTER_ICON_CONTEXT_MENU),
                                 ActionGroup.class);
    }
  }

  private static final class ToggleRenderingAction extends DumbAwareAction {
    private final DocRenderItemImpl item;

    private ToggleRenderingAction(DocRenderItemImpl item) {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC));
      this.item = item;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (item.isValid()) {
        item.toggle();
      }
    }
  }

  public interface Listener {
    void onItemsUpdate(@NotNull Editor editor, @NotNull Collection<DocRenderItemImpl> items, boolean recreateContent);
  }
}
