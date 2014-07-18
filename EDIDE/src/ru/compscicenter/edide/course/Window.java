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

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:54
 */
public class Window implements Comparable{
  public static final String WINDOW_ELEMENT_NAME = "window";
  public static final String LINE_ATTRIBUTE_NAME = "line";
  public static final String START_ATTRIBUTE_NAME = "start";
  public static final String TEXT_ATTRIBUTE_NAME = "text";
  public static final String HINT_ATTRIBUTE_NAME = "hint";
  public static final String POSSIBLE_ANSWER_ATTRIBUTE_NAME = "possibleAnswer";
  public static final String RESOLVE_STATUS_ATTRIBUTE_NAME = "myResolveStatus";
  public static final String LENGTH_ATTRIBUTE_NAME = "myLength";
  public static final String INITIAL_LINE_ATTRIBUTE_NAME = "myInitialLine";
  public static final String INITIAL_START_ATTRIBUTE_NAME = "myInitialStart";
  public static final String INITIAL_LENGTH_ATTRIBUTE_NAME = "myInitialLength";
  public static final String INDEX_ATTRIBUTE_NAME = "myIndex";
  private int line = 0;
  private int start = 0;
  private String text ="";
  private String hint = "";
  private String possibleAnswer = "";
  private boolean myResolveStatus = false;
  private int myLength = text.length();
  private TaskFile myTaskFile;
  private int myIndex = -1;
  private int myInitialLine = -1;
  private int myInitialStart = -1;
  private int myInitialLength = -1;

  //TODO: use it
  public int getIndex() {
    return myIndex;
  }

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


  public void draw(Editor editor, boolean drawSelection, boolean moveCaret) {
    if (myLength == 0) {
      myLength = myInitialLength;
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

  public void init(TaskFile file) {
    myInitialLine = line;
    myInitialLength = text.length();
    myInitialStart = start;
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

  public Window getNext() {
    boolean shouldReturn = false;
    for (Window window : myTaskFile.getWindows()) {
      if (shouldReturn) {
        return window;
      }
      if (window == this) {
        shouldReturn = true;
      }
    }
    return null;
  }

  public void reset() {
    myResolveStatus = false;
    line = myInitialLine;
    start = myInitialStart;
    myLength = myInitialLength;
  }

}