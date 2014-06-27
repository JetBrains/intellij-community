package ru.compscicenter.edide.course;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jdom.Element;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 */
public class Window {
  private int line = 0;
  private int start = 0;
  private String text ="";
  private String hint = "";
  private String possibleAnswer = "";
  private boolean myResolveStatus = false;
  private RangeHighlighter myRangeHighlighter = null;
  private int myOffsetInLine = text.length();

  public void setRangeHighlighter(RangeHighlighter rangeHighlighter) {
    myRangeHighlighter = rangeHighlighter;
  }

  public RangeHighlighter getRangeHighlighter() {
    return myRangeHighlighter;
  }

  public boolean isResolveStatus() {
    return myResolveStatus;
  }

  public void setResolveStatus(boolean resolveStatus) {
    myResolveStatus = resolveStatus;
  }

  public int getOffsetInLine() {
    return myOffsetInLine;
  }

  public void setOffsetInLine(int offsetInLine) {
    myOffsetInLine = offsetInLine;
  }


  public int getStart() {
    return start;
  }

  public void setStart(int start) {
    this.start = start;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public int getLine() {
    return line;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getHint() {
    return hint;
  }

  public String getPossibleAnswer() {
    return possibleAnswer;
  }

  public void draw(Editor editor, boolean drawSelection) {
    if (myOffsetInLine == 0) {
      myOffsetInLine = text.length();
    }
    TextAttributes defaultTestAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    JBColor color;
    if (myResolveStatus) {
      color = JBColor.GREEN;
    }
    else {
      color = JBColor.BLUE;
    }
    int startOffset = editor.getDocument().getLineStartOffset(line) + start;

    RangeHighlighter
      rh = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + myOffsetInLine, HighlighterLayer.LAST + 1,
                                                       new TextAttributes(defaultTestAttributes.getForegroundColor(),
                                                                          defaultTestAttributes.getBackgroundColor(), color,
                                                                          defaultTestAttributes.getEffectType(),
                                                                          defaultTestAttributes.getFontType()),
                                                       HighlighterTargetArea.EXACT_RANGE);
    myRangeHighlighter = rh;
    if (drawSelection) {
      editor.getSelectionModel().setSelection(startOffset, startOffset + myOffsetInLine);
      editor.getCaretModel().moveToOffset(startOffset);
    }

    rh.setGreedyToLeft(true);
    rh.setGreedyToRight(true);
  }


  public int getRealStartOffset(Editor editor) {
    return editor.getDocument().getLineStartOffset(line) + start;
  }

  public Element saveState() {
    Element windowElement = new Element("window");
    windowElement.addContent(Boolean.toString(myResolveStatus));
    return windowElement;
  }
}