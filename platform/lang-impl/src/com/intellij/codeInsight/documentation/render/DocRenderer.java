// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocFontSizePopup;
import com.intellij.codeInsight.documentation.DocumentationActionProvider;
import com.intellij.codeInsight.documentation.DocumentationFontSize;
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil;
import com.intellij.formatting.visualLayer.VirtualFormattingInlaysInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.documentation.QuickDocHighlightingHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorCssFontResolver;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBHtmlPane;
import com.intellij.ui.components.JBHtmlPaneConfiguration;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StyleSheetUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.lang.documentation.DocumentationMarkup.*;

@ApiStatus.Internal
public final class DocRenderer implements CustomFoldRegionRenderer {
  private static final Logger LOG = Logger.getInstance(DocRenderer.class);
  private static final Key<EditorInlineHtmlPane> CACHED_LOADING_PANE = Key.create("cached.loading.pane");
  private static final DocRendererMemoryManager MEMORY_MANAGER = new DocRendererMemoryManager();
  private static final DocRenderImageManager IMAGE_MANAGER = new DocRenderImageManager();

  private static final int MIN_WIDTH = 350;
  private static final int MAX_WIDTH = 680;
  private static final int LEFT_INSET = 14;
  private static final int RIGHT_INSET = 12;
  private static final int TOP_BOTTOM_INSETS = 2;
  private static final int TOP_BOTTOM_MARGINS = 4;
  private static final int LINE_WIDTH = 2;
  private static final int ARC_RADIUS = 5;

  private static StyleSheet ourCachedStyleSheet;
  private static String ourCachedStyleSheetCheckColors = "non-existing";

  private final DocRenderItem myItem;
  private boolean myContentUpdateNeeded;
  private EditorInlineHtmlPane myPane;
  private int myCachedWidth = -1;
  private int myCachedHeight = -1;
  private final @NotNull DocRenderLinkActivationHandler myLinkActivationHandler;

  public DocRenderer(@NotNull DocRenderItem item) {
    this(item, DocRenderDefaultLinkActivationHandler.INSTANCE);
  }

  public DocRenderer(@NotNull DocRenderItem item, @NotNull DocRenderLinkActivationHandler linkActivationHandler) {
    myItem = item;
    myLinkActivationHandler = linkActivationHandler;
  }

  void update(boolean updateSize, boolean updateContent, List<Runnable> foldingTasks) {
    CustomFoldRegion foldRegion = myItem.getFoldRegion();
    if (foldRegion != null) {
      if (updateSize) {
        myCachedWidth = -1;
        myCachedHeight = -1;
      }
      myContentUpdateNeeded = updateContent;
      Runnable task = () -> foldRegion.update();
      if (foldingTasks == null) {
        task.run();
      }
      else {
        foldingTasks.add(task);
      }
    }
  }

  @Override
  public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
    if (myCachedWidth < 0) {
      return myCachedWidth = calcWidth(region.getEditor());
    }
    else {
      return myCachedWidth;
    }
  }

  @Override
  public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
    if (myCachedHeight < 0) {
      Editor editor = region.getEditor();
      int indent = 0;
      // optimize editor opening: skip 'proper' width calculation for 'Loading...' inlays
      if (myItem.getTextToRender() != null) {
        indent = calcInlayStartX() - editor.getInsets().left;
      }
      int width = Math.max(0, calcWidth(editor) - indent - scale(LEFT_INSET) - scale(RIGHT_INSET));
      JComponent component = getRendererComponent(editor, width, null);
      return myCachedHeight = Math.max(editor.getLineHeight(),
                                       component.getPreferredSize().height + scale(TOP_BOTTOM_INSETS) * 2 + scale(TOP_BOTTOM_MARGINS) * 2);
    }
    else {
      return myCachedHeight;
    }
  }

  @Override
  public void paint(@NotNull CustomFoldRegion region,
                    @NotNull Graphics2D g,
                    @NotNull Rectangle2D r,
                    @NotNull TextAttributes textAttributes) {
    int startX = calcInlayStartX();
    int endX = (int)r.getX() + (int)r.getWidth();
    if (startX >= endX) return;
    int margin = scale(TOP_BOTTOM_MARGINS);
    int filledHeight = (int)r.getHeight() - margin * 2;
    if (filledHeight <= 0) return;
    int filledStartY = (int)r.getY() + margin;

    Editor editor = region.getEditor();
    Color defaultBgColor = ((EditorEx)editor).getBackgroundColor();
    Color currentBgColor = textAttributes.getBackgroundColor();
    Color bgColor = currentBgColor == null ? defaultBgColor
                                           : ColorUtil.mix(defaultBgColor, textAttributes.getBackgroundColor(), .5);
    if (currentBgColor != null) {
      g.setColor(bgColor);
      int arcDiameter = ARC_RADIUS * 2;
      if (endX - startX >= arcDiameter) {
        g.fillRect(startX, filledStartY, endX - startX - ARC_RADIUS, filledHeight);
        Object savedHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fillRoundRect(endX - arcDiameter, filledStartY, arcDiameter, filledHeight, arcDiameter, arcDiameter);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, savedHint);
      }
      else {
        g.fillRect(startX, filledStartY, endX - startX, filledHeight);
      }
    }
    Color guideColor = isDebugZombie()
                       ? bgColor
                       : editor.getColorsScheme().getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_GUIDE);
    g.setColor(guideColor);
    g.fillRect(startX, filledStartY, scale(LINE_WIDTH), filledHeight);

    int topBottomInset = scale(TOP_BOTTOM_INSETS);
    int componentWidth = endX - startX - scale(LEFT_INSET) - scale(RIGHT_INSET);
    int componentHeight = filledHeight - topBottomInset * 2;
    if (componentWidth > 0 && componentHeight > 0) {
      EditorInlineHtmlPane component = getRendererComponent(editor, componentWidth, bgColor);
      Graphics dg = g.create(startX + scale(LEFT_INSET), filledStartY + topBottomInset, componentWidth, componentHeight);
      UISettings.setupAntialiasing(dg);
      component.paint(dg);
      dg.dispose();
    }
  }

  @Override
  public @Nullable GutterIconRenderer calcGutterIconRenderer(@NotNull CustomFoldRegion region) {
    assert myItem.getFoldRegion() == region || myItem.getFoldRegion() == null;
    return myItem.calcFoldingGutterIconRenderer();
  }

  @Override
  public ActionGroup getContextMenuGroup(@NotNull CustomFoldRegion region) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CopySelection());
    group.addSeparator();
    group.add(new ToggleRenderingAction(myItem));
    AnAction toggleRenderAllAction = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC_FOR_ALL);
    if (toggleRenderAllAction != null) {
      group.add(toggleRenderAllAction);
    }
    group.add(new ChangeFontSize());

    PsiDocCommentBase comment = getComment();
    for (DocumentationActionProvider provider : DocumentationActionProvider.EP_NAME.getExtensions()) {
      provider.additionalActions(myItem.getEditor(), comment, myItem.getTextToRender()).forEach(group::add);
    }

    return group;
  }

  public DocRenderItem getItem() {
    return myItem;
  }

  private @Nullable PsiDocCommentBase getComment() {
    InlineDocumentation documentation = myItem.getInlineDocumentation();
    return documentation instanceof PsiCommentInlineDocumentation
           ? ((PsiCommentInlineDocumentation)documentation).getComment()
           : null;
  }

  private static int scale(int value) {
    return (int)(value * UISettings.getDefFontScale());
  }

  static int calcWidth(@NotNull Editor editor) {
    int availableWidth = editor.getScrollingModel().getVisibleArea().width;
    if (availableWidth <= 0) {
      // if editor is not shown yet, we create the inlay with maximum possible width,
      // assuming that there's a higher probability that editor will be shown with larger width than with smaller width
      return MAX_WIDTH;
    }
    return Math.max(scale(MIN_WIDTH), Math.min(scale(MAX_WIDTH), availableWidth));
  }

  private int calcInlayStartX() {
    Editor editor = myItem.getEditor();
    RangeHighlighter highlighter = myItem.getHighlighter();
    if (highlighter.isValid()) {
      Document document = editor.getDocument();
      int nextLineNumber = document.getLineNumber(highlighter.getEndOffset()) + 1;
      if (nextLineNumber < document.getLineCount()) {
        int lineStartOffset = document.getLineStartOffset(nextLineNumber);
        int contentStartOffset = CharArrayUtil.shiftForward(document.getImmutableCharSequence(), lineStartOffset, " \t\n");
        int vfmtRightShift = VirtualFormattingInlaysInfo.measureVirtualFormattingInlineInlays(editor, contentStartOffset, contentStartOffset);
        return editor.offsetToXY(contentStartOffset, false, true).x + vfmtRightShift;
      }
    }
    return editor.getInsets().left;
  }

  Rectangle getEditorPaneBoundsWithinRenderer(int width, int height) {
    int relativeX = calcInlayStartX() - myItem.getEditor().getInsets().left + scale(LEFT_INSET);
    int relativeY = scale(TOP_BOTTOM_MARGINS) + scale(TOP_BOTTOM_INSETS);
    return new Rectangle(relativeX, relativeY, width - relativeX - scale(RIGHT_INSET), height - relativeY * 2);
  }

  EditorInlineHtmlPane getRendererComponent(@NotNull Editor editor, int width, @Nullable Color backgroundColor) {
    boolean newInstance = false;
    EditorInlineHtmlPane pane = myPane;
    if (pane == null || myContentUpdateNeeded) {
      myContentUpdateNeeded = false;
      clearCachedComponent();
      if (myItem.getTextToRender() == null) {
        pane = getLoadingPane(editor);
      }
      else {
        myPane = pane = createEditorPane(editor, myItem.getTextToRender(), backgroundColor, false);
        newInstance = true;
      }
    }
    AppUIUtil.targetToDevice(pane, editor.getContentComponent());
    pane.setSize(width, 10_000_000 /* Arbitrary large value, that doesn't lead to overflows and precision loss */);
    if (newInstance) {
      // trigger internal layout, so that image elements are created
      // this is done after 'targetToDevice' call to take correct graphics context into account
      pane.getPreferredSize();
      pane.startImageTracking();
    }
    else if (backgroundColor != null && pane.getBackground().getRGB() != backgroundColor.getRGB()) {
      pane.setBackground(backgroundColor);
      // trigger CSS styles update
      pane.getPreferredSize();
    }
    return pane;
  }

  private EditorInlineHtmlPane getLoadingPane(@NotNull Editor editor) {
    EditorInlineHtmlPane pane = editor.getUserData(CACHED_LOADING_PANE);
    if (pane == null) {
      editor.putUserData(CACHED_LOADING_PANE, pane = createEditorPane(
        editor, CodeInsightBundle.message("doc.render.loading.text"), null, true));
    }
    return pane;
  }

  static void clearCachedLoadingPane(@NotNull Editor editor) {
    editor.putUserData(CACHED_LOADING_PANE, null);
  }

  private EditorInlineHtmlPane createEditorPane(@NotNull Editor editor,
                                                @Nls @NotNull String text,
                                                @Nullable Color backgroundColor,
                                                boolean reusable) {
    EditorInlineHtmlPane pane = new EditorInlineHtmlPane(!reusable, editor);
    pane.getCaret().setSelectionVisible(!reusable);
    pane.setBorder(JBUI.Borders.empty());
    Map<TextAttribute, Object> fontAttributes = new HashMap<>();
    int fontSize = DocumentationFontSize.getDocumentationFontSize().getSize();
    fontAttributes.put(TextAttribute.SIZE, JBUIScale.scale(fontSize));
    // disable kerning for now - laying out all fragments in a file with it takes too much time
    fontAttributes.put(TextAttribute.KERNING, 0);
    pane.setFont(pane.getFont().deriveFont(fontAttributes));
    pane.setScaleFactor(((float)fontSize) / FontSize.SMALL.getSize());
    EditorColorsScheme scheme = editor.getColorsScheme();
    Color textColor = getTextColor(scheme);
    pane.setForeground(textColor);
    pane.setBackground(backgroundColor != null ? backgroundColor : ((EditorEx)editor).getBackgroundColor());
    pane.setSelectedTextColor(textColor);
    pane.setSelectionColor(editor.getSelectionModel().getTextAttributes().getBackgroundColor());
    UIUtil.enableEagerSoftWrapping(pane);
    pane.setText(text);
    pane.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        myLinkActivationHandler.activateLink(e, this);
      }
    });
    pane.getDocument().putProperty("imageCache", IMAGE_MANAGER.getImageProvider());
    return pane;
  }

  void clearCachedComponent() {
    if (myPane != null) {
      Disposer.dispose(myPane);
      myPane = null;
    }
  }

  void dispose() {
    clearCachedComponent();
  }

  private static @NotNull Color getTextColor(@NotNull EditorColorsScheme scheme) {
    TextAttributes attributes = scheme.getAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT);
    Color color = attributes == null ? null : attributes.getForegroundColor();
    return color == null ? scheme.getDefaultForeground() : color;
  }

  private static StyleSheet getStyleSheet(@NotNull Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    Color linkColor = colorsScheme.getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_LINK);
    if (linkColor == null) linkColor = getTextColor(colorsScheme);
    String checkColors = ColorUtil.toHex(linkColor);
    if (!Objects.equals(checkColors, ourCachedStyleSheetCheckColors)) {
      // When updating styles here, consider updating styles in DocumentationHtmlUtil#getDocumentationPaneAdditionalCssRules
      int beforeSpacing = scale(DocumentationHtmlUtil.getSpaceBeforeParagraph());
      int afterSpacing = scale(DocumentationHtmlUtil.getSpaceAfterParagraph());
      @Language("CSS") String input =
        "body {overflow-wrap: anywhere; padding-top: " + scale(2) + "px }" + // supported by JetBrains Runtime
        "pre {white-space: pre-wrap}" +  // supported by JetBrains Runtime
        "a {color: #" + ColorUtil.toHex(linkColor) + "; text-decoration: none}" +
        "." + CLASS_SECTIONS + " {border-spacing: 0}" +
        "." + CLASS_SECTION + " {padding-right: " + scale(5) + "; white-space: nowrap}" +
        "." + CLASS_CONTENT + " {padding: " + beforeSpacing + "px 2px " + afterSpacing + "px 0}";
      StyleSheet result = StyleSheetUtil.loadStyleSheet(input);
      ourCachedStyleSheet = result;
      ourCachedStyleSheetCheckColors = checkColors;
      return result;
    }
    return ourCachedStyleSheet;
  }

  private boolean isDebugZombie() {
    return Registry.is("cache.markup.debug", false) &&
           myItem instanceof DocRenderItemImpl itemImpl &&
           itemImpl.isZombie();
  }

  private static final class ChangeFontSize extends DumbAwareAction {
    ChangeFontSize() {
      super(CodeInsightBundle.messagePointer("javadoc.adjust.font.size"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        DocFontSizePopup.show(editor.getContentComponent(), () -> DocRenderItemUpdater.updateRenderers(editor, true));
      }
    }
  }

  final class EditorInlineHtmlPane extends JBHtmlPane {
    private final List<Image> myImages = new ArrayList<>();
    private final AtomicBoolean myUpdateScheduled = new AtomicBoolean();
    private final AtomicBoolean myRepaintScheduled = new AtomicBoolean();
    private final ImageObserver myImageObserver = new ImageObserver() {
      @Override
      public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        if ((infoflags & (WIDTH | HEIGHT)) != 0) {
          scheduleUpdate();
          return false;
        }
        return true;
      }
    };
    private boolean myRepaintRequested;
    private float myScaleFactor = 1f;

    EditorInlineHtmlPane(boolean trackMemory, Editor editor) {
      super(
        QuickDocHighlightingHelper.getDefaultDocStyleOptions(() -> editor.getColorsScheme(), true),
        JBHtmlPaneConfiguration.builder()
          .imageResolverFactory(pane -> IMAGE_MANAGER.getImageProvider())
          .customStyleSheetProvider(bg -> getStyleSheet(editor))
          .fontResolver(EditorCssFontResolver.getInstance(editor))
          .build()
      );
      if (trackMemory) {
        MEMORY_MANAGER.register(DocRenderer.this, 50 /* rough size estimation */);
      }
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
      myRepaintRequested = true;
    }

    void doWithRepaintTracking(Runnable task) {
      myRepaintRequested = false;
      task.run();
      if (myRepaintRequested) repaintRenderer();
    }

    private void repaintRenderer() {
      CustomFoldRegion foldRegion = myItem.getFoldRegion();
      if (foldRegion != null) {
        foldRegion.repaint();
      }
    }

    void setScaleFactor(float scaleFactor) {
      myScaleFactor = scaleFactor;
    }

    @Override
    public float getContentsScaleFactor() {
      return myScaleFactor;
    }

    @Override
    public void paint(Graphics g) {
      MEMORY_MANAGER.notifyPainted(DocRenderer.this);
      for (Image image : myImages) {
        IMAGE_MANAGER.notifyPainted(image);
      }
      super.paint(g);
    }

    Editor getEditor() {
      return myItem.getEditor();
    }

    void removeSelection() {
      doWithRepaintTracking(() -> select(0, 0));
    }

    boolean hasSelection() {
      return getSelectionStart() != getSelectionEnd();
    }

    @Nullable
    Point getSelectionPositionInEditor() {
      if (myPane != this) {
        return null;
      }
      CustomFoldRegion foldRegion = myItem.getFoldRegion();
      if (foldRegion == null || foldRegion.getRenderer() != DocRenderer.this) {
        return null;
      }
      Point rendererLocation = foldRegion.getLocation();
      if (rendererLocation == null) {
        return null;
      }
      Rectangle boundsWithinRenderer = getEditorPaneBoundsWithinRenderer(foldRegion.getWidthInPixels(), foldRegion.getHeightInPixels());
      Rectangle2D locationInPane;
      try {
        locationInPane = modelToView2D(getSelectionStart());
      }
      catch (BadLocationException e) {
        LOG.error(e);
        locationInPane = new Rectangle();
      }
      return new Point(rendererLocation.x + boundsWithinRenderer.x + (int)locationInPane.getX(),
                       rendererLocation.y + boundsWithinRenderer.y + (int)locationInPane.getY());
    }

    private void scheduleUpdate() {
      if (myUpdateScheduled.compareAndSet(false, true)) {
        SwingUtilities.invokeLater(() -> {
          myRepaintScheduled.set(false);
          myUpdateScheduled.set(false);
          if (this == myPane) {
            CustomFoldRegion foldRegion = myItem.getFoldRegion();
            if (foldRegion != null) {
              DocRenderItemUpdater.getInstance().updateFoldRegions(Collections.singleton(foldRegion), false);
            }
          }
        });
      }
    }

    private void scheduleRepaint() {
      if (!myUpdateScheduled.get() && myRepaintScheduled.compareAndSet(false, true)) {
        SwingUtilities.invokeLater(() -> {
          myRepaintScheduled.set(false);
          if (this == myPane) {
            repaintRenderer();
          }
        });
      }
    }

    void startImageTracking() {
      collectImages(getUI().getRootView(this));
      boolean update = false;
      for (Image image : myImages) {
        IMAGE_MANAGER.setCompletionListener(image, this::scheduleRepaint);
        update |= image.getWidth(myImageObserver) >= 0 || image.getHeight(myImageObserver) >= 0;
      }
      if (update) {
        myImageObserver.imageUpdate(null, WIDTH | HEIGHT, 0, 0, 0, 0);
      }
    }

    private void collectImages(View view) {
      if (view instanceof ImageView) {
        Image image = ((ImageView)view).getImage();
        if (image != null) {
          myImages.add(image);
        }
      }
      int childCount = view.getViewCount();
      for (int i = 0; i < childCount; i++) {
        collectImages(view.getView(i));
      }
    }

    @Override
    public void dispose() {
      MEMORY_MANAGER.unregister(DocRenderer.this);
      myImages.forEach(image -> IMAGE_MANAGER.dispose(image));
    }
  }

  private final class CopySelection extends DumbAwareAction {
    CopySelection() {
      super(CodeInsightBundle.messagePointer("doc.render.copy.action.text"), AllIcons.Actions.Copy);
      AnAction copyAction = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
      if (copyAction != null) {
        copyShortcutFrom(copyAction);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(myPane != null && myPane.hasSelection());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String text = myPane == null ? null : myPane.getSelectedText();
      if (!StringUtil.isEmpty(text)) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
      }
    }
  }

  static final class ToggleRenderingAction extends DumbAwareAction {
    private final DocRenderItem item;

    ToggleRenderingAction(DocRenderItem i) {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC));
      item = i;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      item.toggle();
    }
  }
}
