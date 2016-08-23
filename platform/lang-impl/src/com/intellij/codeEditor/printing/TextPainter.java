/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeEditor.printing;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

class TextPainter extends BasePainter {
  private final DocumentEx myDocument;
  private RangeMarker myRangeToPrint;
  private int myOffset = 0;
  private int myLineNumber = 1;
  private float myLineHeight = -1;
  private float myDescent = -1;
  private double myCharWidth = -1;
  private final Font myPlainFont;
  private final Font myBoldFont;
  private final Font myItalicFont;
  private final Font myBoldItalicFont;
  private final Font myHeaderFont;
  private final EditorHighlighter myHighlighter;
  private final PrintSettings myPrintSettings;
  private final String myFullFileName;
  private final String myShortFileName;
  private int myPageIndex = -1;
  private int myNumberOfPages = -1;
  private int mySegmentEnd;
  private final LineMarkerInfo[] myMethodSeparators;
  private int myCurrentMethodSeparator;
  private final CodeStyleSettings myCodeStyleSettings;
  private final FileType myFileType;
  private boolean myPerformActualDrawing;
  
  private final String myPrintDate;
  private final String myPrintTime;

  @NonNls private static final String DEFAULT_MEASURE_HEIGHT_TEXT = "A";
  @NonNls private static final String DEFAULT_MEASURE_WIDTH_TEXT = "w";
  
  @NonNls private static final String HEADER_TOKEN_PAGE = "PAGE";
  @NonNls private static final String HEADER_TOKEN_TOTALPAGES = "TOTALPAGES";
  @NonNls private static final String HEADER_TOKEN_FILE = "FILE";
  @NonNls private static final String HEADER_TOKEN_FILENAME = "FILENAME";
  @NonNls private static final String HEADER_TOKEN_DATE = "DATE";
  @NonNls private static final String HEADER_TOKEN_TIME = "TIME";
  
  @NonNls private static final String DATE_FORMAT = "yyyy-MM-dd";
  @NonNls private static final String TIME_FORMAT = "HH:mm:ss";    

  public TextPainter(@NotNull DocumentEx editorDocument,
                     EditorHighlighter highlighter,
                     String fullFileName,
                     String shortFileName,
                     @NotNull PsiFile psiFile,
                     FileType fileType,
                     Editor editor) {
    this(editorDocument, highlighter, fullFileName, shortFileName, psiFile.getProject(), fileType,
         FileSeparatorProvider.getFileSeparators(psiFile, editorDocument));
  }

  public TextPainter(@NotNull DocumentEx editorDocument,
                     EditorHighlighter highlighter,
                     String fullFileName,
                     String shortFileName,
                     Project project,
                     FileType fileType,
                     List<LineMarkerInfo> separators) {
    myCodeStyleSettings = CodeStyleSettingsManager.getSettings(project);
    myDocument = editorDocument;
    myPrintSettings = PrintSettings.getInstance();
    String fontName = myPrintSettings.FONT_NAME;
    int fontSize = myPrintSettings.FONT_SIZE;
    myPlainFont = new Font(fontName, Font.PLAIN, fontSize);
    myBoldFont = new Font(fontName, Font.BOLD, fontSize);
    myItalicFont = new Font(fontName, Font.ITALIC, fontSize);
    myBoldItalicFont = new Font(fontName, Font.BOLD | Font.ITALIC, fontSize);
    myHighlighter = highlighter;
    myHeaderFont = new Font(myPrintSettings.FOOTER_HEADER_FONT_NAME, Font.PLAIN, myPrintSettings.FOOTER_HEADER_FONT_SIZE);
    myFullFileName = fullFileName;
    myShortFileName = shortFileName;
    myRangeToPrint = editorDocument.createRangeMarker(0, myDocument.getTextLength());
    myFileType = fileType;
    myMethodSeparators = separators != null ? separators.toArray(new LineMarkerInfo[separators.size()]) : new LineMarkerInfo[0];
    myCurrentMethodSeparator = 0;
    Date date = new Date();
    myPrintDate = new SimpleDateFormat(DATE_FORMAT).format(date);
    myPrintTime = new SimpleDateFormat(TIME_FORMAT).format(date);
  }

  public void setSegment(int segmentStart, int segmentEnd) {
    setSegment(myDocument.createRangeMarker(segmentStart, segmentEnd));
  }

  private void setSegment(RangeMarker marker) {
    if (myRangeToPrint != null) {
      myRangeToPrint.dispose();
    }
    myRangeToPrint = marker;
  }

  private float getLineHeight(Graphics g) {
    if (myLineHeight >= 0) {
      return myLineHeight;
    }
    FontRenderContext fontRenderContext = ((Graphics2D) g).getFontRenderContext();
    LineMetrics lineMetrics = myPlainFont.getLineMetrics(DEFAULT_MEASURE_HEIGHT_TEXT, fontRenderContext);
    myLineHeight = lineMetrics.getHeight();
    return myLineHeight;
  }

  private float getDescent(Graphics g) {
    if (myDescent >= 0) {
      return myDescent;
    }
    FontRenderContext fontRenderContext = ((Graphics2D) g).getFontRenderContext();
    LineMetrics lineMetrics = myPlainFont.getLineMetrics(DEFAULT_MEASURE_HEIGHT_TEXT, fontRenderContext);
    myDescent = lineMetrics.getDescent();
    return myDescent;
  }

  private Font getFont(int type) {
    if (type == Font.BOLD)
      return myBoldFont;
    else if (type == Font.ITALIC)
      return myItalicFont;
    else if (type == Font.ITALIC + Font.BOLD)
      return myBoldItalicFont;
    else
      return myPlainFont;
  }

  boolean isPrintingPass = true;

  @Override
  public int print(final Graphics g, final PageFormat pageFormat, final int pageIndex) throws PrinterException {
    myPerformActualDrawing = false;

    if (myProgress.isCanceled()) {
      return NO_SUCH_PAGE;
    }

    final Graphics2D g2d = (Graphics2D)g;

    if (myNumberOfPages < 0) {
      myProgress.setText(CodeEditorBundle.message("print.file.calculating.number.of.pages.progress"));
      
      if (!calculateNumberOfPages(g2d, pageFormat)) {
        return NO_SUCH_PAGE;
      }
    }

    if (pageIndex >= myNumberOfPages) {
      return NO_SUCH_PAGE;
    }

    isPrintingPass = !isPrintingPass;
    if (!isPrintingPass) {
      while(++myPageIndex < pageIndex) {
        if (!printPageInReadAction(g2d, pageFormat, "print.skip.page.progress")) {
          return NO_SUCH_PAGE;
        }
      }      
      return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
        @Override
        public Integer compute() {
          return isValidRange(myRangeToPrint) ? PAGE_EXISTS : NO_SUCH_PAGE;
        }
      });
    }
    else {
      myPerformActualDrawing = true;
      printPageInReadAction(g2d, pageFormat, "print.file.page.progress");
      return PAGE_EXISTS;
    }
  }
  
  private boolean printPageInReadAction(final Graphics2D g2d, final PageFormat pageFormat, final String progressMessageKey) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (!isValidRange(myRangeToPrint)) {
          return false;
        }
        myProgress.setText(CodeEditorBundle.message(progressMessageKey, myShortFileName, (myPageIndex + 1), myNumberOfPages));
        setSegment(printPage(g2d, pageFormat, myRangeToPrint));
        return true;
      }
    });
  }

  private boolean calculateNumberOfPages(final Graphics2D g2d, final PageFormat pageFormat) {
    myNumberOfPages = 0;
    final Ref<Boolean> firstPage = new Ref<>(Boolean.TRUE);
    final Ref<RangeMarker> tmpMarker = new Ref<>();
    while (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (firstPage.get()) {
          if (!isValidRange(myRangeToPrint)) {
            return false;
          }
          tmpMarker.set(myDocument.createRangeMarker(myRangeToPrint.getStartOffset(), myRangeToPrint.getEndOffset()));
          firstPage.set(Boolean.FALSE);
        }
        RangeMarker range = tmpMarker.get();
        if (!isValidRange(range)) {
          return false;
        }
        tmpMarker.set(printPage(g2d, pageFormat, range));
        range.dispose();
        return true;
      }
    })) {
      if (myProgress.isCanceled()) {
        return false;
      }
      myNumberOfPages++;
    }
    if (!tmpMarker.isNull()) {
      tmpMarker.get().dispose();
    }
    return true;
  }

  private static boolean isValidRange(RangeMarker range) {
    return range != null && range.isValid() && range.getStartOffset() < range.getEndOffset();
  }

  /**
   * Prints a pageful of text from a given range. Return a remaining range to print, or null if there's nothing left.
   */
  private RangeMarker printPage(Graphics2D g2d, PageFormat pageFormat, RangeMarker range) {
    assert isValidRange(range);
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    
    myOffset = startOffset;
    mySegmentEnd = endOffset;
    myLineNumber = myDocument.getLineNumber(myOffset) + 1;
    Rectangle2D.Double clip = new Rectangle2D.Double(pageFormat.getImageableX(), pageFormat.getImageableY(),
                                                     pageFormat.getImageableWidth(), pageFormat.getImageableHeight());
    
    draw(g2d, clip);

    return myOffset > startOffset && myOffset < endOffset ? myDocument.createRangeMarker(myOffset, endOffset) : null;
  }

  private void draw(Graphics2D g2D, Rectangle2D.Double clip) {
    double headerHeight = drawHeader(g2D, clip);
    clip.y += headerHeight;
    clip.height -= headerHeight;
    double footerHeight = drawFooter(g2D, clip);
    clip.height -= footerHeight;

    Rectangle2D.Double border = (Rectangle2D.Double) clip.clone();
    clip.x += getCharWidth(g2D) / 2;
    clip.width -= getCharWidth(g2D);
    if (myPrintSettings.PRINT_LINE_NUMBERS) {
      double numbersStripWidth = calcNumbersStripWidth(g2D, clip) + getCharWidth(g2D) / 2;
      clip.x += numbersStripWidth;
      clip.width -= numbersStripWidth;
    }
    clip.x += getCharWidth(g2D) / 2;
    clip.width -= getCharWidth(g2D);
    drawText(g2D, clip);
    drawBorder(g2D, border);
  }

  private void drawBorder(Graphics2D g, Rectangle2D clip) {
    if (myPrintSettings.DRAW_BORDER && myPerformActualDrawing) {
      Color save = g.getColor();
      g.setColor(Color.black);
      g.draw(clip);
      g.setColor(save);
    }
  }

  private double getCharWidth(Graphics2D g) {
    if (myCharWidth < 0) {
      FontRenderContext fontRenderContext = (g).getFontRenderContext();
      myCharWidth = myPlainFont.getStringBounds(DEFAULT_MEASURE_WIDTH_TEXT, fontRenderContext).getWidth();
    }
    return myCharWidth;
  }

  private void setForegroundColor(Graphics2D g, Color color) {
    if (color == null || !myPrintSettings.COLOR_PRINTING || !myPrintSettings.SYNTAX_PRINTING) {
      color = Color.black;
    }
    g.setColor(color);
  }

  private void setBackgroundColor(Graphics2D g, Color color) {
    if (color == null || !myPrintSettings.COLOR_PRINTING || !myPrintSettings.SYNTAX_PRINTING) {
      color = Color.white;
    }
    g.setColor(color);
  }

  private void setFont(Graphics2D g, Font font) {
    if (!myPrintSettings.SYNTAX_PRINTING) {
      font = myPlainFont;
    }
    g.setFont(font);
  }

  private void drawText(Graphics2D g, Rectangle2D clip) {
    float lineHeight = getLineHeight(g);
    HighlighterIterator hIterator = myHighlighter.createIterator(myOffset);
    if (hIterator.atEnd()) {
      myOffset = mySegmentEnd;
      return;
    }
    LineIterator lIterator = myDocument.createLineIterator();
    lIterator.start(myOffset);
    if (lIterator.atEnd()) {
      myOffset = mySegmentEnd;
      return;
    }
    TextAttributes attributes = hIterator.getTextAttributes();
    Color currentColor = attributes.getForegroundColor();
    Color backColor = attributes.getBackgroundColor();
    Color underscoredColor = attributes.getEffectColor();
    Font currentFont = getFont(attributes.getFontType());
    setForegroundColor(g, currentColor);
    setFont(g, currentFont);
    g.translate(clip.getX(), 0);
    Point2D position = new Point2D.Double(0, clip.getY());
    double lineY = position.getY();

    if (myPerformActualDrawing) {
      getMethodSeparator(lIterator.getLineNumber());
    }

    char[] text = myDocument.getCharsSequence().toString().toCharArray();

    while (!hIterator.atEnd() && !lIterator.atEnd()) {
      int hEnd = hIterator.getEnd();
      int lEnd = lIterator.getEnd();
      int lStart = lIterator.getStart();
      if (hEnd >= lEnd) {
        if (!drawString(g, text, lEnd - lIterator.getSeparatorLength(), myOffset == lStart, position, clip, backColor,
                        underscoredColor)) {
          drawLineNumber(g, 0, lineY);
          break;
        }
        drawLineNumber(g, 0, lineY);
        lIterator.advance();
        myLineNumber++;
        position.setLocation(0, position.getY() + lineHeight);
        lineY = position.getY();
        myOffset = lEnd;

        if (myPerformActualDrawing) {
          LineMarkerInfo marker = getMethodSeparator(lIterator.getLineNumber());
          if (marker != null) {
            Color save = g.getColor();
            setForegroundColor(g, marker.separatorColor);
            UIUtil.drawLine(g, 0, (int)lineY, (int)clip.getWidth(), (int)lineY);
            setForegroundColor(g, save);
          }
        }

        if (position.getY() > clip.getY() + clip.getHeight() - lineHeight) {
          break;
        }
      } else {
        if (hEnd > lEnd - lIterator.getSeparatorLength()) {
          if (!drawString(g, text, lEnd - lIterator.getSeparatorLength(), myOffset == lStart, position, clip, backColor,
                          underscoredColor)) {
            drawLineNumber(g, 0, lineY);
            break;
          }
        } else {
          if (!drawString(g, text, hEnd, myOffset == lStart, position, clip, backColor, underscoredColor)) {
            drawLineNumber(g, 0, lineY);
            break;
          }
        }
        hIterator.advance();
        attributes = hIterator.getTextAttributes();
        Color color = attributes.getForegroundColor();
        if (color == null) {
          color = Color.black;
        }
        if (color != currentColor) {
          setForegroundColor(g, color);
          currentColor = color;
        }
        backColor = attributes.getBackgroundColor();
        underscoredColor = attributes.getEffectColor();
        Font font = getFont(attributes.getFontType());
        if (font != currentFont) {
          setFont(g, font);
          currentFont = font;
        }
        myOffset = hEnd;
      }
    }

    g.translate(-clip.getX(), 0);
  }

  private LineMarkerInfo getMethodSeparator(int line) {
    LineMarkerInfo marker = null;
    LineMarkerInfo tmpMarker;
    while (myCurrentMethodSeparator < myMethodSeparators.length &&
           (tmpMarker = myMethodSeparators[myCurrentMethodSeparator]) != null &&
           FileSeparatorProvider.getDisplayLine(tmpMarker, myDocument) <= line) {
      marker = tmpMarker;
      myCurrentMethodSeparator++;
    }
    return marker;
  }

  private double drawHeader(Graphics2D g, Rectangle2D clip) {
    LineMetrics lineMetrics = getHeaderFooterLineMetrics(g);
    double w = clip.getWidth();
    double x = clip.getX();
    double y = clip.getY();
    double h = 0;
    boolean wasDrawn = false;

    String headerText1 = myPrintSettings.FOOTER_HEADER_TEXT1;
    if (headerText1 != null && headerText1.length() > 0 &&
        PrintSettings.HEADER.equals(myPrintSettings.FOOTER_HEADER_PLACEMENT1)) {
      h = drawHeaderOrFooterLine(g, x, y, w, headerText1, myPrintSettings.FOOTER_HEADER_ALIGNMENT1);
      wasDrawn = true;
      y += h;
    }

    String headerText2 = myPrintSettings.FOOTER_HEADER_TEXT2;
    if (headerText2 != null && headerText2.length() > 0 &&
        PrintSettings.HEADER.equals(myPrintSettings.FOOTER_HEADER_PLACEMENT2)) {
      if (PrintSettings.LEFT.equals(myPrintSettings.FOOTER_HEADER_ALIGNMENT1) &&
          PrintSettings.RIGHT.equals(myPrintSettings.FOOTER_HEADER_ALIGNMENT2) &&
          wasDrawn) {
        y -= h;
      }
      h = drawHeaderOrFooterLine(g, x, y, w, headerText2, myPrintSettings.FOOTER_HEADER_ALIGNMENT2);
      y += h;
      wasDrawn = true;
    }
    return wasDrawn ? y - clip.getY() + lineMetrics.getHeight() / 3 : 0;
  }

  private double drawFooter(Graphics2D g, Rectangle2D clip) {
    LineMetrics lineMetrics = getHeaderFooterLineMetrics(g);
    double w = clip.getWidth();
    double x = clip.getX();
    double y = clip.getY() + clip.getHeight();
    boolean wasDrawn = false;
    double h = 0;
    y -= lineMetrics.getHeight();
    String headerText2 = myPrintSettings.FOOTER_HEADER_TEXT2;
    if (headerText2 != null && headerText2.length() > 0 &&
        PrintSettings.FOOTER.equals(myPrintSettings.FOOTER_HEADER_PLACEMENT2)) {
      h = drawHeaderOrFooterLine(g, x, y, w, headerText2, myPrintSettings.FOOTER_HEADER_ALIGNMENT2);
      wasDrawn = true;
    }

    String headerText1 = myPrintSettings.FOOTER_HEADER_TEXT1;
    if (headerText1 != null && headerText1.length() > 0 &&
        PrintSettings.FOOTER.equals(myPrintSettings.FOOTER_HEADER_PLACEMENT1)) {
      y -= lineMetrics.getHeight();
      if (PrintSettings.LEFT.equals(myPrintSettings.FOOTER_HEADER_ALIGNMENT1) &&
          PrintSettings.RIGHT.equals(myPrintSettings.FOOTER_HEADER_ALIGNMENT2) &&
          wasDrawn) {
        y += h;
      }
      drawHeaderOrFooterLine(g, x, y, w, headerText1, myPrintSettings.FOOTER_HEADER_ALIGNMENT1);
      wasDrawn = true;
    }
    return wasDrawn ? clip.getY() + clip.getHeight() - y + lineMetrics.getHeight() / 4 : 0;
  }

  private double drawHeaderOrFooterLine(Graphics2D g, double x, double y, double w, String headerText,
                                        String alignment) {
    FontRenderContext fontRenderContext = g.getFontRenderContext();
    LineMetrics lineMetrics = getHeaderFooterLineMetrics(g);
    float lineHeight = lineMetrics.getHeight();
    if (myPerformActualDrawing) {
      headerText = convertHeaderText(headerText);
      g.setFont(myHeaderFont);
      g.setColor(Color.black);
      float descent = lineMetrics.getDescent();
      double width = myHeaderFont.getStringBounds(headerText, fontRenderContext).getWidth() + getCharWidth(g);
      float yPos = (float) (lineHeight - descent + y);
      if (PrintSettings.LEFT.equals(alignment)) {
        drawStringToGraphics(g, headerText, x, yPos);
      } else if (PrintSettings.CENTER.equals(alignment)) {
        drawStringToGraphics(g, headerText, (float) (x + (w - width) / 2), yPos);
      } else if (PrintSettings.RIGHT.equals(alignment)) {
        drawStringToGraphics(g, headerText, (float) (x + w - width), yPos);
      }
    }
    return lineHeight;
  }

  private String convertHeaderText(String s) {
    StringBuilder result = new StringBuilder("");
    int start = 0;
    boolean isExpression = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '$') {
        String token = s.substring(start, i);
        if (isExpression) {
          if (HEADER_TOKEN_PAGE.equals(token)) {
            result.append(myPageIndex + 1);
          } else if (HEADER_TOKEN_TOTALPAGES.equals(token)) {
            result.append(myNumberOfPages);
          } else if (HEADER_TOKEN_FILE.equals(token)) {
            result.append(myFullFileName);
          } else if (HEADER_TOKEN_FILENAME.equals(token)) {
            result.append(myShortFileName);
          } else if (HEADER_TOKEN_DATE.equals(token)) {
            result.append(myPrintDate);
          } else if (HEADER_TOKEN_TIME.equals(token)) {
            result.append(myPrintTime);
          }
        } else {
          result.append(token);
        }
        isExpression = !isExpression;
        start = i + 1;
      }
    }
    if (!isExpression && start < s.length()) {
      result.append(s.substring(start, s.length()));
    }
    return result.toString();
  }

  private LineMetrics getHeaderFooterLineMetrics(Graphics2D g) {
    FontRenderContext fontRenderContext = g.getFontRenderContext();
    return myHeaderFont.getLineMetrics(DEFAULT_MEASURE_HEIGHT_TEXT, fontRenderContext);
  }

  private double calcNumbersStripWidth(Graphics2D g, Rectangle2D clip) {
    if (!myPrintSettings.PRINT_LINE_NUMBERS) {
      return 0;
    }
    int maxLineNumber = myLineNumber + (int) (clip.getHeight() / getLineHeight(g));
    FontRenderContext fontRenderContext = (g).getFontRenderContext();
    double numbersStripWidth = 0;
    for (int i = myLineNumber; i < maxLineNumber; i++) {
      double width = myPlainFont.getStringBounds(String.valueOf(i), fontRenderContext).getWidth();
      if (numbersStripWidth < width) {
        numbersStripWidth = width;
      }
    }
    return numbersStripWidth;
  }

  private void drawLineNumber(Graphics2D g, double x, double y) {
    if (!myPrintSettings.PRINT_LINE_NUMBERS || !myPerformActualDrawing) {
      return;
    }
    FontRenderContext fontRenderContext = (g).getFontRenderContext();
    double width = myPlainFont.getStringBounds(String.valueOf(myLineNumber), fontRenderContext).getWidth() + getCharWidth(g);
    Color savedColor = g.getColor();
    Font savedFont = g.getFont();
    g.setColor(Color.black);
    g.setFont(myPlainFont);
    drawStringToGraphics(g, String.valueOf(myLineNumber), x - width, getLineHeight(g) - getDescent(g) + y);
    g.setColor(savedColor);
    g.setFont(savedFont);
  }

  private boolean drawString(Graphics2D g, char[] text, int end, boolean lineStart, Point2D position, Rectangle2D clip, 
                             Color backColor, Color underscoredColor) {
    boolean toContinue = true; 
    if (end >= mySegmentEnd) {
      end = mySegmentEnd;
      toContinue = false;
    }
    if (myOffset >= end) return toContinue;
    boolean isInClip = (getLineHeight(g) + position.getY() >= clip.getY()) && (position.getY() <= clip.getY() + clip.getHeight());
    if (!isInClip) return toContinue;
    
    if (myPrintSettings.WRAP) {
      double w = getTextSegmentWidth(text, myOffset, end - myOffset, position.getX(), g);
      if (position.getX() + w > clip.getWidth()) {
        IntArrayList breakOffsets = LineWrapper.calcBreakOffsets(text, myOffset, end, lineStart, position.getX(), clip.getWidth(),
                                                                 (t, start, count, x) -> getTextSegmentWidth(t, start, count, x, g));
        for (int i = 0; i < breakOffsets.size(); i++) {
          int breakOffset = breakOffsets.get(i);
          drawTabbedString(g, text, breakOffset - myOffset, position, backColor, underscoredColor);
          position.setLocation(0, position.getY() + getLineHeight(g));
          if (position.getY() > clip.getY() + clip.getHeight() - getLineHeight(g)) {
            return false;
          }
        }
      }
    }
    drawTabbedString(g, text, end - myOffset, position, backColor, underscoredColor);
    return toContinue;
  }

  private void drawTabbedString(final Graphics2D g, char[] text, int length, Point2D position, Color backColor, Color underscoredColor) {
    ProgressManager.checkCanceled();
    if (length <= 0) return;
    double xStart = position.getX();
    double x = position.getX();
    double y = getLineHeight(g) - getDescent(g) + position.getY();
    if (backColor != null && myPerformActualDrawing) {
      Color savedColor = g.getColor();
      setBackgroundColor(g, backColor);
      double w = getTextSegmentWidth(text, myOffset, length, position.getX(), g);
      g.fill(new Area(new Rectangle2D.Double(position.getX(),
                                             y - getLineHeight(g) + getDescent(g),
                                             w,
                                             getLineHeight(g))));
      g.setColor(savedColor);
    }

    int start = myOffset;

    for (int i = myOffset; i < myOffset + length; i++) {
      if (text[i] != '\t')
        continue;
      if (i > start) {
        String s = new String(text, start, i - start);
        x += drawStringToGraphics(g, s, x, y);
      }
      x = nextTabStop(g, x);
      start = i + 1;
    }

    if (myOffset + length > start) {
      String s = new String(text, start, myOffset + length - start);
      x += drawStringToGraphics(g, s, x, y);
    }

    if (underscoredColor != null && myPerformActualDrawing) {
      Color savedColor = g.getColor();
      setForegroundColor(g, underscoredColor);
      double w = getTextSegmentWidth(text, myOffset, length, position.getX(), g);
      UIUtil.drawLine(g, (int)position.getX(), (int)y + 1, (int)(xStart + w), (int)(y + 1));
      g.setColor(savedColor);
    }
    position.setLocation(x, position.getY());
    myOffset += length;
  }

  private double drawStringToGraphics(Graphics2D g, String s, double x, double y) {
    if (!myPrintSettings.PRINT_AS_GRAPHICS) {
      if (myPerformActualDrawing) {
        g.drawString(s, (float)x, (float)y);
      }
      return g.getFontMetrics().stringWidth(s);
    } else {
      GlyphVector v = g.getFont().createGlyphVector(g.getFontRenderContext(), s);
      if (myPerformActualDrawing) {
        g.translate(x, y);
        g.fill(v.getOutline());
        g.translate(-x, -y);
      }

      return v.getLogicalBounds().getWidth();
    }
  }
  private double getTextSegmentWidth(char[] text, int offset, int length, double x, Graphics2D g) {
    int start = offset;
    double startX = x;

    for (int i = offset; i < offset + length; i++) {
      if (text[i] != '\t')
        continue;

      if (i > start) {
        x += getStringWidth(g, text, start, i - start);
      }
      x = nextTabStop(g, x);
      start = i + 1;
    }

    if (offset + length > start) {
      x += getStringWidth(g, text, start, offset + length - start);
    }

    return x - startX;
  }

  private static double getStringWidth(Graphics2D g, char[] text, int start, int count) {
    String s = new String(text, start, count);
    GlyphVector v = g.getFont().createGlyphVector(g.getFontRenderContext(), s);

    return v.getLogicalBounds().getWidth();
  }

  public double nextTabStop(Graphics2D g, double x) {
    double tabSize = myCodeStyleSettings.getTabSize(myFileType);
    if (tabSize <= 0) {
      tabSize = 1;
    }

    tabSize *= g.getFont().getStringBounds(" ", g.getFontRenderContext()).getWidth();

    int nTabs = (int) (x / tabSize);
    return (nTabs + 1) * tabSize;
  }

  @Override
  void dispose() {
    setSegment(null);
  }
}
