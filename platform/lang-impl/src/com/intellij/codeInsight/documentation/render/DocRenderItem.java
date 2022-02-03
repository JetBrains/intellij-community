// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocFontSizePopup;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.lang.documentation.InlineDocumentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.*;
import java.util.function.BooleanSupplier;

import static com.intellij.codeInsight.documentation.render.InlineDocumentationImplKt.findInlineDocumentation;

public final class DocRenderItem {
  private static final Key<Collection<DocRenderItem>> OUR_ITEMS = Key.create("doc.render.items");
  private static final Key<Disposable> LISTENERS_DISPOSABLE = Key.create("doc.render.listeners.disposable");

  final Editor editor;
  final RangeHighlighter highlighter;
  @Nls String textToRender;
  CustomFoldRegion foldRegion;

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

  static void setItemsToEditor(@NotNull Editor editor, @NotNull DocRenderPassFactory.Items itemsToSet, boolean collapseNewItems) {
    Collection<DocRenderItem> items;
    Collection<DocRenderItem> existing = editor.getUserData(OUR_ITEMS);
    if (existing == null) {
      if (itemsToSet.isEmpty()) return;
      editor.putUserData(OUR_ITEMS, items = new ArrayList<>());
    }
    else {
      items = existing;
    }
    keepScrollingPositionWhile(editor, () -> {
      List<Runnable> foldingTasks = new ArrayList<>();
      List<DocRenderItem> itemsToUpdateRenderers = new ArrayList<>();
      List<String> itemsToUpdateText = new ArrayList<>();
      boolean updated = false;
      for (Iterator<DocRenderItem> it = items.iterator(); it.hasNext(); ) {
        DocRenderItem existingItem = it.next();
        DocRenderPassFactory.Item matchingNewItem = existingItem.isValid() ? itemsToSet.removeItem(existingItem.highlighter) : null;
        if (matchingNewItem == null) {
          updated |= existingItem.remove(foldingTasks);
          it.remove();
        }
        else if (matchingNewItem.textToRender != null && !matchingNewItem.textToRender.equals(existingItem.textToRender)) {
          itemsToUpdateRenderers.add(existingItem);
          itemsToUpdateText.add(matchingNewItem.textToRender);
        }
        else {
          existingItem.updateIcon(foldingTasks);
        }
      }
      Collection<DocRenderItem> newRenderItems = new ArrayList<>();
      for (DocRenderPassFactory.Item item : itemsToSet) {
        DocRenderItem newItem = new DocRenderItem(editor, item.textRange, collapseNewItems ? null : item.textToRender);
        newRenderItems.add(newItem);
        if (collapseNewItems) {
          updated |= newItem.toggle(foldingTasks);
          itemsToUpdateRenderers.add(newItem);
          itemsToUpdateText.add(item.textToRender);
        }
      }
      editor.getFoldingModel().runBatchFoldingOperation(() -> foldingTasks.forEach(Runnable::run), true, false);
      for (int i = 0; i < itemsToUpdateRenderers.size(); i++) {
        itemsToUpdateRenderers.get(i).textToRender = itemsToUpdateText.get(i);
      }
      updateRenderers(itemsToUpdateRenderers, true);
      items.addAll(newRenderItems);
      return updated;
    });
    setupListeners(editor, items.isEmpty());
  }

  private static void setupListeners(@NotNull Editor editor, boolean disable) {
    if (disable) {
      Disposable listenersDisposable = editor.getUserData(LISTENERS_DISPOSABLE);
      if (listenersDisposable != null) {
        Disposer.dispose(listenersDisposable);
        editor.putUserData(LISTENERS_DISPOSABLE, null);
      }
    }
    else {
      if (editor.getUserData(LISTENERS_DISPOSABLE) == null) {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.setDefaultHandler(() -> updateRenderers(editor, true));
        connection.subscribe(EditorColorsManager.TOPIC);
        connection.subscribe(LafManagerListener.TOPIC);
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
          @Override
          public void editorReleased(@NotNull EditorFactoryEvent event) {
            if (event.getEditor() == editor) {
              // this ensures renderers are not kept for the released editors
              setItemsToEditor(editor, new DocRenderPassFactory.Items(), false);
            }
          }
        }, connection);
        editor.getCaretModel().addCaretListener(new MyCaretListener(), connection);

        DocRenderSelectionManager selectionManager = new DocRenderSelectionManager(editor);
        Disposer.register(connection, selectionManager);

        DocRenderMouseEventBridge mouseEventBridge = new DocRenderMouseEventBridge(selectionManager);
        editor.addEditorMouseListener(mouseEventBridge, connection);
        editor.addEditorMouseMotionListener(mouseEventBridge, connection);

        IconVisibilityController iconVisibilityController = new IconVisibilityController();
        editor.addEditorMouseListener(iconVisibilityController, connection);
        editor.addEditorMouseMotionListener(iconVisibilityController, connection);
        editor.getScrollingModel().addVisibleAreaListener(iconVisibilityController, connection);
        Disposer.register(connection, iconVisibilityController);

        editor.getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener(editor), connection);
        ((EditorEx)editor).getFoldingModel().addListener(new MyFoldingListener(), connection);

        Disposer.register(connection, () -> DocRenderer.clearCachedLoadingPane(editor));

        editor.putUserData(LISTENERS_DISPOSABLE, connection);
      }
    }
  }

  private static void keepScrollingPositionWhile(@NotNull Editor editor, @NotNull BooleanSupplier task) {
    EditorScrollingPositionKeeper keeper = new EditorScrollingPositionKeeper(editor);
    keeper.savePosition();
    if (task.getAsBoolean()) keeper.restorePosition(false);
  }

  @Nullable
  public static DocRenderItem getItemAroundOffset(@NotNull Editor editor, int offset) {
    Collection<DocRenderItem> items = editor.getUserData(OUR_ITEMS);
    if (items == null || items.isEmpty()) return null;
    Document document = editor.getDocument();
    if (offset < 0 || offset > document.getTextLength()) return null;
    int line = document.getLineNumber(offset);
    DocRenderItem itemOnAdjacentLine = items.stream().filter(i -> {
      if (!i.isValid()) return false;
      int startLine = document.getLineNumber(i.highlighter.getStartOffset());
      int endLine = document.getLineNumber(i.highlighter.getEndOffset());
      return line >= startLine - 1 && line <= endLine + 1;
    }).min(Comparator.comparingInt(i -> i.highlighter.getStartOffset())).orElse(null);
    if (itemOnAdjacentLine != null) return itemOnAdjacentLine;

    Project project = editor.getProject();
    if (project == null) return null;

    DocRenderItem foundItem = null;
    int foundStartOffset = 0;
    for (DocRenderItem item : items) {
      if (!item.isValid()) continue;
      InlineDocumentation documentation = item.getInlineDocumentation();
      if (documentation == null) continue;
      TextRange ownerTextRange = documentation.getDocumentationOwnerRange();
      if (ownerTextRange == null || !ownerTextRange.containsOffset(offset)) continue;
      int startOffset = ownerTextRange.getStartOffset();
      if (foundItem != null && foundStartOffset >= startOffset) continue;
      foundItem = item;
      foundStartOffset = startOffset;
    }
    return foundItem;
  }

  static void resetToDefaultState(@NotNull Editor editor) {
    Collection<DocRenderItem> items = editor.getUserData(OUR_ITEMS);
    if (items == null) return;
    boolean editorSetting = DocRenderManager.isDocRenderingEnabled(editor);
    keepScrollingPositionWhile(editor, () -> {
      List<Runnable> foldingTasks = new ArrayList<>();
      boolean updated = false;
      for (DocRenderItem item : items) {
        if (item.isValid() && (item.foldRegion == null) == editorSetting) {
          updated |= item.toggle(foldingTasks);
        }
      }
      editor.getFoldingModel().runBatchFoldingOperation(() -> foldingTasks.forEach(Runnable::run), true, false);
      return updated;
    });
  }

  public static CustomFoldRegionRenderer createDemoRenderer(@NotNull Editor editor) {
    DocRenderItem item = new DocRenderItem(editor, new TextRange(0, 0), CodeInsightBundle.message(
      "documentation.rendered.documentation.with.href.link"));
    return new DocRenderer(item);
  }

  private DocRenderItem(@NotNull Editor editor, @NotNull TextRange textRange, @Nullable @Nls String textToRender) {
    this.editor = editor;
    this.textToRender = textToRender;
    highlighter = editor.getMarkupModel()
      .addRangeHighlighter(null, textRange.getStartOffset(), textRange.getEndOffset(), 0, HighlighterTargetArea.EXACT_RANGE);
    updateIcon(null);
  }

  private boolean isValid() {
    return highlighter.isValid() &&
           highlighter.getStartOffset() < highlighter.getEndOffset() &&
           new ItemLocation(highlighter).matches(foldRegion);
  }

  private boolean remove(@NotNull Collection<Runnable> foldingTasks) {
    highlighter.dispose();
    if (foldRegion != null && foldRegion.isValid()) {
      foldingTasks.add(() -> foldRegion.getEditor().getFoldingModel().removeFoldRegion(foldRegion));
      return true;
    }
    return false;
  }

  void toggle() {
    toggle(null);
  }

  private boolean toggle(@Nullable Collection<Runnable> foldingTasks) {
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

  @Nullable InlineDocumentation getInlineDocumentation() {
    if (highlighter.isValid()) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(Objects.requireNonNull(editor.getProject()));
      PsiFile file = psiDocumentManager.getPsiFile(editor.getDocument());
      if (file != null) {
        return findInlineDocumentation(file, TextRange.create(highlighter));
      }
    }
    return null;
  }

  @Nullable DocumentationTarget getInlineDocumentationTarget() {
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

  private void updateIcon(List<Runnable> foldingTasks) {
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

  AnAction createToggleAction() {
    return new ToggleRenderingAction(this);
  }

  private void setIconVisible(boolean visible) {
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

  @Nullable
  public String getTextToRender() {
    return textToRender;
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

  private static class MyCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
      onCaretUpdate(event);
    }

    @Override
    public void caretAdded(@NotNull CaretEvent event) {
      onCaretUpdate(event);
    }

    private static void onCaretUpdate(@NotNull CaretEvent event) {
      Caret caret = event.getCaret();
      if (caret == null) return;
      int caretOffset = caret.getOffset();
      FoldRegion foldRegion = caret.getEditor().getFoldingModel().getCollapsedRegionAtOffset(caretOffset);
      if (foldRegion instanceof CustomFoldRegion && caretOffset > foldRegion.getStartOffset()) {
        CustomFoldRegionRenderer renderer = ((CustomFoldRegion)foldRegion).getRenderer();
        if (renderer instanceof DocRenderer) {
          ((DocRenderer)renderer).myItem.toggle();
        }
      }
    }
  }

  private static final class MyVisibleAreaListener implements VisibleAreaListener {
    private int lastWidth;
    private AffineTransform lastFrcTransform;

    private MyVisibleAreaListener(@NotNull Editor editor) {
      lastWidth = DocRenderer.calcWidth(editor);
      lastFrcTransform = getTransform(editor);
    }

    private static AffineTransform getTransform(Editor editor) {
      return FontInfo.getFontRenderContext(editor.getContentComponent()).getTransform();
    }

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
      if (e.getNewRectangle().isEmpty()) return; // ignore switching between tabs
      Editor editor = e.getEditor();
      int newWidth = DocRenderer.calcWidth(editor);
      AffineTransform transform = getTransform(editor);
      if (newWidth != lastWidth || !Objects.equals(transform, lastFrcTransform)) {
        lastWidth = newWidth;
        lastFrcTransform = transform;
        updateRenderers(editor, false);
      }
    }
  }

  private static class MyFoldingListener implements FoldingListener {
    @Override
    public void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
      if (region instanceof CustomFoldRegion) {
        CustomFoldRegionRenderer renderer = ((CustomFoldRegion)region).getRenderer();
        if (renderer instanceof DocRenderer) {
          ((DocRenderer)renderer).dispose();
        }
      }
    }
  }

  class MyGutterIconRenderer extends GutterIconRenderer implements DumbAware {
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
    private final DocRenderItem item;

    private ToggleRenderingAction(DocRenderItem item) {
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

  static class ChangeFontSize extends DumbAwareAction {
    ChangeFontSize() {
      super(CodeInsightBundle.messagePointer("javadoc.adjust.font.size"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        DocFontSizePopup.show(editor.getContentComponent(), () -> updateRenderers(editor, true));
      }
    }
  }

  private static class IconVisibilityController implements EditorMouseListener, EditorMouseMotionListener, VisibleAreaListener, Disposable {
    private DocRenderItem myCurrentItem;
    private Editor myQueuedEditor;

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
      doUpdate(e.getEditor(), e);
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent e) {
      doUpdate(e.getEditor(), e);
    }

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
      Editor editor = e.getEditor();
      if (((EditorImpl)editor).isCursorHidden()) return;
      if (myQueuedEditor == null) {
        myQueuedEditor = editor;
        // delay update: multiple visible area updates within same EDT event will cause only one icon update,
        // and we'll not observe the item in inconsistent state during toggling
        SwingUtilities.invokeLater(() -> {
          if (myQueuedEditor != null && !myQueuedEditor.isDisposed()) {
            doUpdate(myQueuedEditor, null);
          }
          myQueuedEditor = null;
        });
      }
    }

    private void doUpdate(@NotNull Editor editor, @Nullable EditorMouseEvent event) {
      int y = 0;
      int offset = -1;
      if (event == null) {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info != null) {
          Point screenPoint = info.getLocation();
          JComponent component = editor.getComponent();

          Point componentPoint = new Point(screenPoint);
          SwingUtilities.convertPointFromScreen(componentPoint, component);

          if (new Rectangle(component.getSize()).contains(componentPoint)) {
            Point editorPoint = new Point(screenPoint);
            SwingUtilities.convertPointFromScreen(editorPoint, editor.getContentComponent());
            y = editorPoint.y;
            offset = editor.visualPositionToOffset(new VisualPosition(editor.yToVisualLine(y), 0));
          }
        }
      }
      else {
        y = event.getMouseEvent().getY();
        offset = event.getOffset();
      }
      DocRenderItem item = offset < 0 ? null : findItem(editor, y, offset);
      if (item != myCurrentItem) {
        if (myCurrentItem != null) myCurrentItem.setIconVisible(false);
        myCurrentItem = item;
        if (myCurrentItem != null) myCurrentItem.setIconVisible(true);
      }
    }

    private static DocRenderItem findItem(Editor editor, int y, int neighborOffset) {
      Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(neighborOffset);
      int searchStartOffset = document.getLineStartOffset(Math.max(0, lineNumber - 1));
      int searchEndOffset = document.getLineEndOffset(lineNumber);
      Collection<DocRenderItem> items = editor.getUserData(OUR_ITEMS);
      assert items != null;
      for (DocRenderItem item : items) {
        RangeHighlighter highlighter = item.highlighter;
        if (highlighter.isValid() && highlighter.getStartOffset() <= searchEndOffset && highlighter.getEndOffset() >= searchStartOffset) {
          int itemStartY = 0;
          int itemEndY = 0;
          if (item.foldRegion == null) {
            itemStartY = editor.visualLineToY(editor.offsetToVisualLine(highlighter.getStartOffset(), false));
            itemEndY = editor.visualLineToY(editor.offsetToVisualLine(highlighter.getEndOffset(), true)) + editor.getLineHeight();
          }
          else {
            CustomFoldRegion cfr = item.foldRegion;
            Point location = cfr.getLocation();
            if (location != null) {
              itemStartY = location.y;
              itemEndY = itemStartY + cfr.getHeightInPixels();
            }
          }
          if (y >= itemStartY && y < itemEndY) return item;
          break;
        }
      }
      return null;
    }

    @Override
    public void dispose() {
      myCurrentItem = null;
      myQueuedEditor = null;
    }
  }
}
