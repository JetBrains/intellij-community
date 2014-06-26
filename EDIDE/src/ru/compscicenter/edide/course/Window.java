package ru.compscicenter.edide.course;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jdom.Element;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 */
public class Window {
  private int line = 0;
  private int start = 0;
  private String text = "default text";
  private String hint = "";
  private String possibleAnswer = "";
  private int offsetInLine = text.length();
  private boolean myResolveStatus = false;

  public Window() {
  }
  public int getLine() {
    return line;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public void draw(Editor editor, boolean drawSelection) {
    final TextAttributes ta = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    //ta.setEffectColor(color);
    final int startOffset = editor.getDocument().getLineStartOffset(line) + start;

    RangeHighlighter
      rh = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + text.length(), HighlighterLayer.LAST + 1, ta, HighlighterTargetArea.EXACT_RANGE);

    //TODO:return;
    //setOffsets(rh.getStartOffset(), rh.getEndOffset());
    if (drawSelection) {
      editor.getSelectionModel().setSelection(startOffset, startOffset + text.length());
      editor.getCaretModel().moveToOffset(startOffset);
    }

    rh.setGreedyToLeft(true);
    rh.setGreedyToRight(true);
  }

  public boolean getResolveStatus() {
    //return myResolveStatus;
    return false;
  }
  //
  //public int getOffsetInLine() {
  //  return offsetInLine;
  //}


  public void setResolveStatus(boolean resolveStatus) {
    myResolveStatus = resolveStatus;
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