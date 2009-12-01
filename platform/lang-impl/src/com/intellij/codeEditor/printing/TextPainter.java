/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.List;

public class TextPainter implements Printable {
  private final DocumentEx myDocument;

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
  private final String myFileName;
  private int myPageIndex;
  private int mySegmentEnd;
  private final LineMarkerInfo[] myMethodSeparators;
  private int myCurrentMethodSeparator;
  private final CodeStyleSettings myCodeStyleSettings;
  private final FileType myFileType;
  private ProgressIndicator myProgress;
  @NonNls private static final String DEFAULT_MEASURE_HEIGHT_TEXT = "A";
  @NonNls private static final String DEFAULT_MEASURE_WIDTH_TEXT = "w";
  @NonNls private static final String HEADER_TOKEN_PAGE = "PAGE";
  @NonNls private static final String HEADER_TOKEN_FILE = "FILE";

  public TextPainter(DocumentEx editorDocument, EditorHighlighter highlighter, String fileName, final PsiFile psiFile,
                     final Project project) {
    myCodeStyleSettings = CodeStyleSettingsManager.getSettings(project);
    myDocument = editorDocument;
    myPrintSettings = PrintSettings.getInstance();
    String fontName = myPrintSettings.FONT_NAME;
    int fontSize = myPrintSettings.FONT_SIZE;
    myPlainFont = new Font(fontName, Font.PLAIN, fontSize);
    myBoldFont = new Font(fontName, Font.BOLD, fontSize);
    myItalicFont = new Font(fontName, Font.ITALIC, fontSize);
    myBoldItalicFont = new Font(fontName, Font.BOLD + Font.ITALIC, fontSize);
    myHighlighter = highlighter;
    myHeaderFont = new Font(myPrintSettings.FOOTER_HEADER_FONT_NAME, Font.PLAIN,
                            myPrintSettings.FOOTER_HEADER_FONT_SIZE);
    myFileName = fileName;
    mySegmentEnd = myDocument.getTextLength();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    myFileType = psiFile.getFileType();


    final List<LineMarkerInfo> methodSeparators = FileSeparatorProvider.getInstance().getFileSeparators(psiFile, editorDocument);
    myMethodSeparators = methodSeparators != null ? methodSeparators.toArray(new LineMarkerInfo[methodSeparators.size()]) : new LineMarkerInfo[0];
    myCurrentMethodSeparator = 0;
  }

  public void setProgress(ProgressIndicator progress) {
    myProgress = progress;
  }

  public void setSegment(int segmentStart, int segmentEnd, int firstLineNumber) {
    myOffset = segmentStart;
    mySegmentEnd = segmentEnd;
    myLineNumber = firstLineNumber;
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

  public int print(final Graphics g, final PageFormat pageFormat, final int pageIndex) throws PrinterException {
    if (myOffset >= mySegmentEnd || myProgress.isCanceled()) {
      return Printable.NO_SUCH_PAGE;
    }
    isPrintingPass = !isPrintingPass;
    if (!isPrintingPass) {
      return Printable.PAGE_EXISTS;
    }

    myProgress.setText(CodeEditorBundle.message("print.file.page.progress", myFileName, (pageIndex + 1)));

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myPageIndex = pageIndex;
        Graphics2D g2D = (Graphics2D) g;
        Rectangle2D.Double clip = new Rectangle2D.Double(pageFormat.getImageableX(), pageFormat.getImageableY(),
                                                         pageFormat.getImageableWidth(),
                                                         pageFormat.getImageableHeight());

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
    });

    return Printable.PAGE_EXISTS;
  }

  private void drawBorder(Graphics2D g, Rectangle2D clip) {
    if (myPrintSettings.DRAW_BORDER) {
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

    while (myCurrentMethodSeparator < myMethodSeparators.length) {
      LineMarkerInfo marker = myMethodSeparators[myCurrentMethodSeparator];
      if (marker != null && marker.startOffset >= lIterator.getEnd()) break;
      myCurrentMethodSeparator++;
    }

    while (!hIterator.atEnd() && !lIterator.atEnd()) {
      int hEnd = hIterator.getEnd();
      int lEnd = lIterator.getEnd();
      int lStart = lIterator.getStart();
      if (hEnd >= lEnd) {
        if (!drawString(g, lEnd - lIterator.getSeparatorLength(), lEnd - lStart, position, clip, backColor,
                        underscoredColor)) {
          drawLineNumber(g, 0, lineY);
          break;
        }
        drawLineNumber(g, 0, lineY);
        lIterator.advance();
        myLineNumber++;

        if (myCurrentMethodSeparator < myMethodSeparators.length) {
          LineMarkerInfo marker = myMethodSeparators[myCurrentMethodSeparator];
          if (marker != null && marker.startOffset < lEnd) {
            Color save = g.getColor();
            setForegroundColor(g, marker.separatorColor);
            UIUtil.drawLine(g, 0, (int)lineY, (int)clip.getWidth(), (int)lineY);
            setForegroundColor(g, save);
            myCurrentMethodSeparator++;
          }
        }

        position.setLocation(0, position.getY() + lineHeight);
        lineY = position.getY();
        myOffset = lEnd;
        if (position.getY() > clip.getY() + clip.getHeight() - lineHeight) {
          break;
        }
      } else {
        if (hEnd > lEnd - lIterator.getSeparatorLength()) {
          if (!drawString(g, lEnd - lIterator.getSeparatorLength(), lEnd - lStart, position, clip, backColor,
                          underscoredColor)) {
            drawLineNumber(g, 0, lineY);
            break;
          }
        } else {
          if (!drawString(g, hEnd, lEnd - lStart, position, clip, backColor, underscoredColor)) {
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
    headerText = convertHeaderText(headerText);
    g.setFont(myHeaderFont);
    g.setColor(Color.black);
    FontRenderContext fontRenderContext = g.getFontRenderContext();
    LineMetrics lineMetrics = getHeaderFooterLineMetrics(g);
    float lineHeight = lineMetrics.getHeight();
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
    return lineHeight;
  }

  private String convertHeaderText(String s) {
    StringBuffer result = new StringBuffer("");
    int start = 0;
    boolean isExpression = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '$') {
        String token = s.substring(start, i);
        if (isExpression) {
          if (HEADER_TOKEN_PAGE.equals(token)) {
            result.append(myPageIndex + 1);
          } else if (HEADER_TOKEN_FILE.equals(token)) {
            result.append(myFileName);
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
    if (!myPrintSettings.PRINT_LINE_NUMBERS) {
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

  private boolean drawString(Graphics2D g, int end, int colNumber, Point2D position, Rectangle2D clip, Color backColor,
                             Color underscoredColor) {
    ProgressManager.checkCanceled();
    if (myOffset >= end)
      return true;
    char[] text = myDocument.getCharsSequence().toString().toCharArray(); //TODO: Make drawTabbedString work with CharSequence instead.
    boolean isInClip = (getLineHeight(g) + position.getY() >= clip.getY()) &&
                       (position.getY() <= clip.getY() + clip.getHeight());
    if (!isInClip)
      return true;
    return drawTabbedString(g, text, end - myOffset, position, clip, colNumber, backColor, underscoredColor);
  }

  private boolean drawTabbedString(Graphics2D g, char[] text, int length, Point2D position, Rectangle2D clip,
                                   int colNumber, Color backColor, Color underscoredColor) {
    boolean ret = true;
    if (myOffset + length >= mySegmentEnd) {
      ret = false;
      length = mySegmentEnd - myOffset;
    }
    if (length <= 0) { // How it can be?
      return false;
    }
    if (myPrintSettings.WRAP) {
      double w = getTextSegmentWidth(text, myOffset, length, position.getX(), g);
      if (position.getX() + w > clip.getWidth()) {
        IntArrayList breakOffsets = calcBreakOffsets(g, text, myOffset, myOffset + length, colNumber, position, clip);
        int startOffset = myOffset;
        for (int i = 0; i < breakOffsets.size(); i++) {
          int breakOffset = breakOffsets.get(i);
          drawTabbedString(g, text, breakOffset - myOffset, position, clip, colNumber, backColor, underscoredColor);
          position.setLocation(0, position.getY() + getLineHeight(g));
          if (position.getY() > clip.getY() + clip.getHeight() - getLineHeight(g)) {
            return false;
          }
        }
        if (myOffset > startOffset) {
          drawTabbedString(g, text, startOffset + length - myOffset, position, clip, colNumber, backColor,
                           underscoredColor);
        }
        return ret;
      }
    }
    double xStart = position.getX();
    double x = position.getX();
    double y = getLineHeight(g) - getDescent(g) + position.getY();
    if (backColor != null) {
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

    if (underscoredColor != null) {
      Color savedColor = g.getColor();
      setForegroundColor(g, underscoredColor);
      double w = getTextSegmentWidth(text, myOffset, length, position.getX(), g);
      UIUtil.drawLine(g, (int)position.getX(), (int)y + 1, (int)(xStart + w), (int)(y + 1));
      g.setColor(savedColor);
    }
    position.setLocation(x, position.getY());
    myOffset += length;
    return ret;
  }

  private double drawStringToGraphics(Graphics2D g, String s, double x, double y) {
    if (!myPrintSettings.PRINT_AS_GRAPHICS) {
      g.drawString(s, (float) x, (float) y);
      return g.getFontMetrics().stringWidth(s);
    } else {
      GlyphVector v = g.getFont().createGlyphVector(g.getFontRenderContext(), s);
      g.translate(x, y);
      g.fill(v.getOutline());
      g.translate(-x, -y);

      return v.getLogicalBounds().getWidth();
    }
  }

  private IntArrayList calcBreakOffsets(Graphics2D g, char[] text, int offset, int endOffset, int colNumber,
                                        Point2D position, Rectangle2D clip) {
    IntArrayList breakOffsets = new IntArrayList();
    int nextOffset = offset;
    double x = position.getX();
    while (true) {
      int prevOffset = nextOffset;
      nextOffset = calcWordBreakOffset(g, text, nextOffset, endOffset, x, clip);
      if (nextOffset == offset || nextOffset == prevOffset && colNumber == 0) {
        nextOffset = calcCharBreakOffset(g, text, nextOffset, endOffset, x, clip);
        if (nextOffset == prevOffset) { //it shouldn't be, but if clip.width is <= 1...
          return breakOffsets;
        }
      }
      if (nextOffset >= endOffset) {
        break;
      }
      breakOffsets.add(nextOffset);
      colNumber = 0;
      x = 0;
    }
    return breakOffsets;
  }

  private int calcCharBreakOffset(Graphics2D g, char[] text, int offset, int endOffset, double x, Rectangle2D clip) {
    double newX = x;
    int breakOffset = offset;
    while (breakOffset < endOffset) {
      int nextOffset = breakOffset + 1;
      newX += getTextSegmentWidth(text, breakOffset, nextOffset - breakOffset, newX, g);
      if (newX > clip.getWidth()) {
        return breakOffset;
      }
      breakOffset = nextOffset;
    }
    return breakOffset;
  }

  private int calcWordBreakOffset(Graphics2D g, char[] text, int offset, int endOffset, double x, Rectangle2D clip) {
    double newX = x;
    int breakOffset = offset;
    while (breakOffset < endOffset) {
      int nextOffset = getNextWordBreak(text, breakOffset, endOffset);
      newX += getTextSegmentWidth(text, breakOffset, nextOffset - breakOffset, newX, g);
      if (newX > clip.getWidth()) {
        return breakOffset;
      }
      breakOffset = nextOffset;
    }
    return breakOffset;
  }

  private int getNextWordBreak(char[] text, int offset, int endOffset) {
    boolean isId = Character.isJavaIdentifierPart(text[offset]);
    for (int i = offset + 1; i < endOffset; i++) {
      if (isId != Character.isJavaIdentifierPart(text[i])) {
        return i;
      }
    }
    return endOffset;
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

  private double getStringWidth(Graphics2D g, char[] text, int start, int count) {
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
}
