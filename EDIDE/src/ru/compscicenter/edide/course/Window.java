package ru.compscicenter.edide.course;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 * Implementation of windows which user should type in
 */
public class Window implements Comparable {
  private static final String WINDOW_ELEMENT_NAME = "window";
  private static final String LINE_ATTRIBUTE_NAME = "line";
  private static final String START_ATTRIBUTE_NAME = "start";
  private static final String TEXT_ATTRIBUTE_NAME = "text";
  private static final String HINT_ATTRIBUTE_NAME = "hint";
  private static final String POSSIBLE_ANSWER_ATTRIBUTE_NAME = "possibleAnswer";
  private static final String RESOLVE_STATUS_ATTRIBUTE_NAME = "myResolveStatus";
  private static final String LENGTH_ATTRIBUTE_NAME = "myLength";
  private static final String INITIAL_LINE_ATTRIBUTE_NAME = "myInitialLine";
  private static final String INITIAL_START_ATTRIBUTE_NAME = "myInitialStart";
  private static final String INITIAL_LENGTH_ATTRIBUTE_NAME = "myInitialLength";
  private static final String INDEX_ATTRIBUTE_NAME = "myIndex";
  private int line = 0;
  private int start = 0;
  private String text = "";
  private String hint = "";
  private String possibleAnswer = "";
  private boolean myResolveStatus = false;
  private int myLength = text.length();
  private TaskFile myTaskFile;
  private int myIndex = -1;
  private int myInitialLine = -1;
  private int myInitialStart = -1;
  private int myInitialLength = -1;

  public void setIndex(int index) {
    myIndex = index;
  }

  /**
   * Saves window state for serialization
   *
   * @return xml element with attributes typical for window
   */
  public Element saveState() {
    Element windowElement = new Element(WINDOW_ELEMENT_NAME);
    windowElement.setAttribute(LINE_ATTRIBUTE_NAME, String.valueOf(line));
    windowElement.setAttribute(START_ATTRIBUTE_NAME, String.valueOf(start));
    windowElement.setAttribute(TEXT_ATTRIBUTE_NAME, text);
    windowElement.setAttribute(HINT_ATTRIBUTE_NAME, hint);
    windowElement.setAttribute(POSSIBLE_ANSWER_ATTRIBUTE_NAME, possibleAnswer);
    windowElement.setAttribute(RESOLVE_STATUS_ATTRIBUTE_NAME, String.valueOf(myResolveStatus));
    windowElement.setAttribute(LENGTH_ATTRIBUTE_NAME, String.valueOf(myLength));
    windowElement.setAttribute(INITIAL_LINE_ATTRIBUTE_NAME, String.valueOf(myInitialLine));
    windowElement.setAttribute(INITIAL_START_ATTRIBUTE_NAME, String.valueOf(myInitialStart));
    windowElement.setAttribute(INITIAL_LENGTH_ATTRIBUTE_NAME, String.valueOf(myInitialLength));
    windowElement.setAttribute(INDEX_ATTRIBUTE_NAME, String.valueOf(myIndex));
    return windowElement;
  }

  /**
   * initializes window after reopening of project or IDE restart
   *
   * @param windowElement xml element which contains information about window
   */
  public void loadState(Element windowElement) {
    try {
      line = windowElement.getAttribute(LINE_ATTRIBUTE_NAME).getIntValue();
      start = windowElement.getAttribute(START_ATTRIBUTE_NAME).getIntValue();
      text = windowElement.getAttributeValue(TEXT_ATTRIBUTE_NAME);
      hint = windowElement.getAttributeValue(HINT_ATTRIBUTE_NAME);
      possibleAnswer = windowElement.getAttributeValue(POSSIBLE_ANSWER_ATTRIBUTE_NAME);
      myResolveStatus = windowElement.getAttribute(RESOLVE_STATUS_ATTRIBUTE_NAME).getBooleanValue();
      myLength = windowElement.getAttribute(LENGTH_ATTRIBUTE_NAME).getIntValue();
      myInitialLine = windowElement.getAttribute(INITIAL_LINE_ATTRIBUTE_NAME).getIntValue();
      myInitialStart = windowElement.getAttribute(INITIAL_START_ATTRIBUTE_NAME).getIntValue();
      myInitialLength = windowElement.getAttribute(INITIAL_LENGTH_ATTRIBUTE_NAME).getIntValue();
      myIndex = windowElement.getAttribute(INDEX_ATTRIBUTE_NAME).getIntValue();
    }
    catch (DataConversionException e) {
      e.printStackTrace();
    }
  }

  public boolean isResolveStatus() {
    return myResolveStatus;
  }

  public void setResolveStatus(boolean resolveStatus) {
    myResolveStatus = resolveStatus;
  }

  public int getLength() {
    return myLength;
  }

  public void setLength(int length) {
    myLength = length;
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


  /**
   * Draw task window with color according to its status
   */
  public void draw(Editor editor, boolean drawSelection, boolean moveCaret) {
    TextAttributes defaultTestAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    JBColor color = myResolveStatus ? JBColor.GREEN : JBColor.BLUE;
    int startOffset = editor.getDocument().getLineStartOffset(line) + start;
    RangeHighlighter
      rh = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + myLength, HighlighterLayer.LAST + 1,
                                                       new TextAttributes(defaultTestAttributes.getForegroundColor(),
                                                                          defaultTestAttributes.getBackgroundColor(), color,
                                                                          defaultTestAttributes.getEffectType(),
                                                                          defaultTestAttributes.getFontType()),
                                                       HighlighterTargetArea.EXACT_RANGE);
    if (drawSelection) {
      editor.getSelectionModel().setSelection(startOffset, startOffset + myLength);
    }
    if (moveCaret) {
      editor.getCaretModel().moveToOffset(startOffset);
    }
    rh.setGreedyToLeft(true);
    rh.setGreedyToRight(true);
  }

  public int getRealStartOffset(Editor editor) {
    return editor.getDocument().getLineStartOffset(line) + start;
  }

  /**
   * Initializes window
   *
   * @param file task file which window belongs to
   */
  public void init(TaskFile file) {
    myInitialLine = line;
    myLength = text.length();
    myInitialLength = myLength;
    myInitialStart = start;
    myTaskFile = file;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    if (!(o instanceof Window)) {
      throw new ClassCastException();
    }
    Window window = (Window)o;
    if (window.getTaskFile() != myTaskFile) {
      throw new ClassCastException();
    }
    if (window.getLine() == line && window.getStart() == start) {
      return 0;
    }
    if (window.getLine() == line) {
      return window.start < start ? 1 : -1;
    }
    return window.getLine() < line ? 1 : -1;
  }

  public Window getNext() {
    List<Window> windows = myTaskFile.getWindows();
    if (myIndex + 1 < windows.size()) {
      return windows.get(myIndex + 1);
    }
    return null;
  }

  /**
   * Returns window to its initial state
   */
  public void reset() {
    myResolveStatus = false;
    line = myInitialLine;
    start = myInitialStart;
    myLength = myInitialLength;
  }
}