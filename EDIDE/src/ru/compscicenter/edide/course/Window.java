package ru.compscicenter.edide.course;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 */
public class Window implements Comparable{
  @Expose
  private int line = 0;
  @Expose
  private int start = 0;
  @Expose
  private String text ="";
  @Expose
  private String hint = "";
  @Expose
  private String possibleAnswer = "";
  @Expose
  private boolean myResolveStatus = false;
  private RangeHighlighter myRangeHighlighter = null;
  @Expose
  private int myOffsetInLine = text.length();
  private TaskFile myTaskFile;

  public Element saveState() {
    Element windowElement = new Element("window");
    windowElement.setAttribute("line", Integer.toString(line));
    windowElement.setAttribute("start", Integer.toString(start));
    windowElement.setAttribute("text", text);
    windowElement.setAttribute("hint", hint);
    windowElement.setAttribute("possibleAnswer", possibleAnswer);
    windowElement.setAttribute("myResolveStatus", Boolean.toString(myResolveStatus));
    windowElement.setAttribute("myOffsetInLine", Integer.toString(myOffsetInLine));
    return windowElement;
  }

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

  public void setHint(String hint) {
    this.hint = hint;
  }

  public void setPossibleAnswer(String possibleAnswer) {
    this.possibleAnswer = possibleAnswer;
  }

  public int getRealStartOffset(Editor editor) {
    return editor.getDocument().getLineStartOffset(line) + start;
  }

  public void setParent(TaskFile file) {
    myTaskFile = file;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    if (!(o instanceof Window)) {
      throw  new ClassCastException();
    }
    Window window = (Window) o;
    if (window.getTaskFile() != myTaskFile) {
      throw new ClassCastException();
    }
    if (window.getLine() == line && window.getStart() == start) {
      return 0;
    }
    if (window.getLine() == line) {
      if (window.start < start) {
        return 1;
      } else {
        return -1;
      }
    }
    if (window.getLine() < line) {
      return 1;
    }
    return -1;
  }

}