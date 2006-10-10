package com.intellij.codeInsight.hint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.ui.LightweightHint;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;

public class EditorFragmentComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.EditorFragmentComponent");

  private EditorFragmentComponent(EditorEx editor, int startLine, int endLine, boolean showFolding, boolean showGutter) {
    Document doc = editor.getDocument();
    final int endOffset = endLine < doc.getLineCount() ? doc.getLineEndOffset(endLine) : doc.getTextLength();
    int textWidth = editor.getMaxWidthInRange(doc.getLineStartOffset(startLine), endOffset);

    FoldingModelEx foldingModel = (FoldingModelEx) editor.getFoldingModel();
    boolean isFoldingEnabled = foldingModel.isFoldingEnabled();
    if (!showFolding) {
      foldingModel.setFoldingEnabled(false);
    }

    Point p1 = editor.logicalPositionToXY(new LogicalPosition(startLine, 0));
    Point p2 = editor.logicalPositionToXY(new LogicalPosition(Math.max(endLine, startLine + 1), 0));
    int y1 = p1.y;
    int y2 = p2.y;

    int savedScrollOfset = editor.getScrollingModel().getHorizontalScrollOffset();
    if (savedScrollOfset > 0) {
      editor.stopOptimizedScrolling();
      editor.getScrollingModel().scrollHorizontally(0);
    }

    final Image textImage = new BufferedImage(textWidth, y2 - y1, BufferedImage.TYPE_INT_RGB);
    Graphics textGraphics = textImage.getGraphics();

    final JComponent rowHeader;
    final Image markersImage;
    if (showGutter) {
      rowHeader = editor.getGutterComponentEx();
      markersImage = new BufferedImage(rowHeader.getWidth(), y2 - y1, BufferedImage.TYPE_INT_RGB);
      Graphics markerGraphics = markersImage.getGraphics();

      markerGraphics.translate(0, -y1);
      markerGraphics.setClip(0, y1, rowHeader.getWidth(), y2 - y1);
      markerGraphics.setColor(getBackgroundColor(editor));
      markerGraphics.fillRect(0, y1, rowHeader.getWidth(), y2 - y1);
      rowHeader.paint(markerGraphics);
    }
    else {
      rowHeader = null;
      markersImage = null;
    }

    textGraphics.translate(0, -y1);
    textGraphics.setClip(0, y1, textWidth, y2 - y1);
    editor.setCaretVisible(false);
    editor.getContentComponent().paint(textGraphics);
    editor.setCaretVisible(true);

    if (!showFolding) {
      foldingModel.setFoldingEnabled(isFoldingEnabled);
    }

    if (savedScrollOfset > 0) {
      editor.stopOptimizedScrolling();
      editor.getScrollingModel().scrollHorizontally(savedScrollOfset);
    }

    JComponent component = new JComponent() {
      public Dimension getPreferredSize() {
        return new Dimension(textImage.getWidth(null) +(markersImage == null ? 0 : markersImage.getWidth(null)), textImage.getHeight(null));
      }

      protected void paintComponent(Graphics graphics) {
        if (markersImage != null) {
          graphics.drawImage(markersImage, 0, 0, null);
          graphics.drawImage(textImage, rowHeader.getWidth(), 0, null);
        }
        else {
          graphics.drawImage(textImage, 0, 0, null);
        }
      }
    };

    setLayout(new BorderLayout());
    add(component);

    final Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_FOLDING_TREE_COLOR);

    Border outsideBorder = BorderFactory.createLineBorder(borderColor, 1);
    Border insideBorder = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
  }

  /**
   * @param x <code>x</code> coordinate in layered pane coordinate system.
   * @param y <code>y</code> coordinate in layered pane coordinate system.
   */
  public static LightweightHint showEditorFragmentHintAt(
      Editor editor,
      TextRange range,
      int x,
      int y,
      boolean showUpward, boolean showFolding) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;

    int startLine = editor.offsetToLogicalPosition(range.getStartOffset()).line;
    int endLine = Math.min(editor.offsetToLogicalPosition(range.getEndOffset()).line + 1, editor.getDocument().getLineCount() - 1);

    if (editor.logicalPositionToXY(new LogicalPosition(startLine, 0)).y >= editor.logicalPositionToXY(new LogicalPosition(endLine, 0)).y) return null;

    EditorFragmentComponent fragmentComponent = createEditorFragmentComponent(editor, startLine, endLine, showFolding, true);


    if (showUpward) {
      y -= fragmentComponent.getPreferredSize().height + 10;
      y  = Math.max(0,y);
    }

    Point p = new Point(x, y);
    LightweightHint hint = new MyComponentHint(fragmentComponent);
    HintManager.getInstance().showEditorHint(hint, editor, p, HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, 0, false);
    return hint;
  }

  public static EditorFragmentComponent createEditorFragmentComponent(Editor editor,
                                                                      int startLine,
                                                                      int endLine,
                                                                      boolean showFolding, boolean showGutter) {
    final EditorEx editorEx = (EditorEx)editor;
    Color backColor = getBackgroundColor(editor);
    editorEx.setBackgroundColor(backColor);
    EditorFragmentComponent fragmentComponent = new EditorFragmentComponent(editorEx, startLine, endLine,
                                                                            showFolding, showGutter);
    fragmentComponent.setBackground(backColor);

    editorEx.resetBackgourndColor();
    return fragmentComponent;
  }

  public static LightweightHint showEditorFragmentHint(Editor editor,TextRange range, boolean showFolding){
    int x = -2;
    int y = 0;

    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    Point point = SwingUtilities.convertPoint(editorComponent, x, y, layeredPane);

    return showEditorFragmentHintAt(editor, range, point.x, point.y, true, showFolding);
  }

  public interface DeclarationRangeHandler {
    @NotNull TextRange getDeclarationRange(@NotNull PsiElement container);
  }

  private static Map<Class,DeclarationRangeHandler> ourDeclarationRangeRegistry = new HashMap<Class, DeclarationRangeHandler>();

  public static void setDeclarationHandler(@NotNull Class clazz, DeclarationRangeHandler handler) {
    ourDeclarationRangeRegistry.put(clazz, handler);
  }

  // Q: not a good place?
  public static @NotNull TextRange getDeclarationRange(PsiElement container) {
    if (container instanceof PsiMethod){
      PsiMethod method = (PsiMethod)container;
      int startOffset = method.getModifierList().getTextRange().getStartOffset();
      int endOffset = method.getThrowsList().getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    else if (container instanceof PsiClass){
      PsiClass aClass = (PsiClass)container;
      if (aClass instanceof PsiAnonymousClass){
        PsiConstructorCall call = (PsiConstructorCall)aClass.getParent();
        int startOffset = call.getTextRange().getStartOffset();
        int endOffset = call.getArgumentList().getTextRange().getEndOffset();
        return new TextRange(startOffset, endOffset);
      }
      else{
        int startOffset = aClass.getModifierList().getTextRange().getStartOffset();
        int endOffset = aClass.getImplementsList().getTextRange().getEndOffset();
        return new TextRange(startOffset, endOffset);
      }
    }
    else if (container instanceof PsiClassInitializer){
      PsiClassInitializer initializer = (PsiClassInitializer)container;
      int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
      int endOffset = initializer.getBody().getTextRange().getStartOffset();
      return new TextRange(startOffset, endOffset);
    }
    else if (container instanceof XmlTag){
      XmlTag xmlTag = (XmlTag)container;
      int endOffset = xmlTag.getTextRange().getStartOffset();

      for (PsiElement child = xmlTag.getFirstChild(); child != null; child = child.getNextSibling()) {
        endOffset = child.getTextRange().getEndOffset();
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          IElementType tokenType = token.getTokenType();
          if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END || tokenType == XmlTokenType.XML_TAG_END) break;
        }
      }

      return new TextRange(xmlTag.getTextRange().getStartOffset(), endOffset);
    }
    else {
      for(Class clazz:ourDeclarationRangeRegistry.keySet()) {
        if (clazz.isInstance(container)) {
          final DeclarationRangeHandler handler = ourDeclarationRangeRegistry.get(clazz);
          if (handler != null) return handler.getDeclarationRange(container);
        }
      }

      LOG.assertTrue(false);
      return null;
    }
  }

  public static Color getBackgroundColor(Editor editor){
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    Color color = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    if (color == null){
      color = colorsScheme.getDefaultBackground();
    }
    return color;
  }

  private static class MyComponentHint extends LightweightHint {
    public MyComponentHint(JComponent component) {
      super(component);
      setForceLightweightPopup(true);
    }

    public void hide() {
      // needed for Alt-Q multiple times
      // Q: not good?
      SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            MyComponentHint.super.hide();
          }
        }
      );
    }
  }
}