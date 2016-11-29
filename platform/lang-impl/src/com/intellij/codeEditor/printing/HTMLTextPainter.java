/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

class HTMLTextPainter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeEditor.printing.HTMLTextPainter");

  private int myOffset = 0;
  private final EditorHighlighter myHighlighter;
  private final String myText;
  private final String myFileName;
  private final String myHTMLFileName;
  private int mySegmentEnd;
  private final PsiFile myPsiFile;
  private final Document myDocument;
  private int lineCount;
  private int myFirstLineNumber;
  private final boolean myPrintLineNumbers;
  private int myColumn;
  private final LineMarkerInfo[] myMethodSeparators;
  private int myCurrentMethodSeparator;
  private final Project myProject;
  private final Map<TextAttributes, String> myStyleMap = new HashMap<>();
  private final Map<Color, String> mySeparatorStyles = new HashMap<>();

  public HTMLTextPainter(PsiFile psiFile, Project project, String dirName, boolean printLineNumbers) {
    myProject = project;
    myPsiFile = psiFile;
    myPrintLineNumbers = printLineNumbers;
    myHighlighter = HighlighterFactory.createHighlighter(project, psiFile.getVirtualFile());

//    String fileType = FileTypeManager.getInstance().getType(psiFile.getVirtualFile().getName());
//    myForceFonts =
//      FileTypeManager.TYPE_HTML.equals(fileType) ||
//      FileTypeManager.TYPE_XML.equals(fileType) ||
//      FileTypeManager.TYPE_JSP.equals(fileType);

    myText = psiFile.getText();
    myHighlighter.setText(myText);
    mySegmentEnd = myText.length();
    myFileName = psiFile.getVirtualFile().getPresentableUrl();
    myHTMLFileName = dirName + File.separator + ExportToHTMLManager.getHTMLFileName(psiFile);

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    myDocument = psiDocumentManager.getDocument(psiFile);

    ArrayList<LineMarkerInfo> methodSeparators = new ArrayList<>();
    if (myDocument != null) {
      final List<LineMarkerInfo> separators = FileSeparatorProvider.getFileSeparators(psiFile, myDocument);
      if (separators != null) {
        methodSeparators.addAll(separators);
      }
    }

    myMethodSeparators = methodSeparators.toArray(new LineMarkerInfo[methodSeparators.size()]);
    myCurrentMethodSeparator = 0;
  }

  public void setSegment(int segmentStart, int segmentEnd, int firstLineNumber) {
    myOffset = segmentStart;
    mySegmentEnd = segmentEnd;
    myFirstLineNumber = firstLineNumber;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void paint(TreeMap refMap, FileType fileType) throws FileNotFoundException {
    HighlighterIterator hIterator = myHighlighter.createIterator(myOffset);
    if(hIterator.atEnd()) return;
    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(myHTMLFileName), CharsetToolkit.UTF8_CHARSET);
    lineCount = myFirstLineNumber;
    TextAttributes prevAttributes = null;
    Iterator refKeys = null;

    int refOffset = -1;
    PsiReference ref = null;
    if(refMap != null) {
      refKeys = refMap.keySet().iterator();
      if(refKeys.hasNext()) {
        Integer key = (Integer)refKeys.next();
        ref = (PsiReference)refMap.get(key);
        refOffset = key.intValue();
      }
    }

    int referenceEnd = -1;
    try {
      writeHeader(writer, new File(myFileName).getName());
      if (myFirstLineNumber == 0) {
        writeLineNumber(writer);
      }
      String closeTag = null;

      getMethodSeparator(hIterator.getStart());

      while(!hIterator.atEnd()) {
        TextAttributes textAttributes = hIterator.getTextAttributes();
        int hStart = hIterator.getStart();
        int hEnd = hIterator.getEnd();
        if (hEnd > mySegmentEnd) break;

        boolean haveNonWhiteSpace = false;
        for(int offset = hStart; offset < hEnd; offset++) {
          char c = myText.charAt(offset);
          if (c != ' ' && c != '\t') {
            haveNonWhiteSpace = true;
            break;
          }
        }
        if (!haveNonWhiteSpace) {
          // don't write separate spans for whitespace-only text fragments
          writeString(writer, myText, hStart, hEnd - hStart, fileType);
          hIterator.advance();
          continue;
        }

        if(refOffset > 0 && hStart <= refOffset && hEnd > refOffset) {
          referenceEnd = writeReferenceTag(writer, ref);
        }
//        if(myForceFonts || !equals(prevAttributes, textAttributes)) {
        if(!equals(prevAttributes, textAttributes) && referenceEnd < 0 ) {
          if(closeTag != null) {
            writer.write(closeTag);
          }
          closeTag = writeFontTag(writer, textAttributes);
          prevAttributes = textAttributes;
        }

        writeString(writer, myText, hStart, hEnd - hStart, fileType);
//        if(closeTag != null) {
//          writer.write(closeTag);
//        }
        if(referenceEnd > 0 && hEnd >= referenceEnd) {
          writer.write("</a>");
          referenceEnd = -1;
          if(refKeys.hasNext()) {
            Integer key = (Integer)refKeys.next();
            ref = (PsiReference)refMap.get(key);
            refOffset = key.intValue();
          }
        }
        hIterator.advance();
      }
      if(closeTag != null) {
        writer.write(closeTag);
      }
      writeFooter(writer);
    }
    catch(IOException e) {
      LOG.error(e.getMessage(), e);
    }
    finally {
      try {
        writer.close();
      }
      catch(IOException e) {
        LOG.error(e.getMessage(), e);
      }
    }
  }

  private LineMarkerInfo getMethodSeparator(int offset) {
    if (myDocument == null) return null;
    int line = myDocument.getLineNumber(Math.max(0, Math.min(myDocument.getTextLength(), offset)));
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

  private int writeReferenceTag(Writer writer, PsiReference ref) throws IOException {
    PsiElement refClass = ref.resolve();

    PsiFile refFile = refClass.getContainingFile();
    String refPackageName = PsiDirectoryFactory.getInstance(myProject).getQualifiedName(refFile.getContainingDirectory(), false);
    String psiPackageName = PsiDirectoryFactory.getInstance(myProject).getQualifiedName(myPsiFile.getContainingDirectory(), false);

    StringBuffer fileName = new StringBuffer();
    if (!psiPackageName.equals(refPackageName)) {
      StringTokenizer tokens = new StringTokenizer(psiPackageName, ".");
      while(tokens.hasMoreTokens()) {
        tokens.nextToken();
        fileName.append("../");
      }

      StringTokenizer refTokens = new StringTokenizer(refPackageName, ".");
      while(refTokens.hasMoreTokens()) {
        String token = refTokens.nextToken();
        fileName.append(token);
        fileName.append('/');
      }
    }
    fileName.append(ExportToHTMLManager.getHTMLFileName(refFile));
    //noinspection HardCodedStringLiteral
    writer.write("<a href=\""+fileName+"\">");
    return ref.getElement().getTextRange().getEndOffset();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String writeFontTag(Writer writer, TextAttributes textAttributes) throws IOException {
//    "<FONT COLOR=\"#000000\">"
    writer.write("<span class=\"" + myStyleMap.get(textAttributes) + "\">");
    return "</span>";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void writeString(Writer writer, CharSequence charArray, int start, int length, FileType fileType) throws IOException {
    for(int i=start; i<start+length; i++) {
      char c = charArray.charAt(i);
      if(c=='<') {
        writeChar(writer, "&lt;");
      }
      else if(c=='>') {
        writeChar(writer, "&gt;");
      }
      else if (c=='&') {
        writeChar(writer, "&amp;");
      }
      else if (c=='\"') {
        writeChar(writer, "&quot;");
      }
      else if (c == '\t') {
        int tabSize = CodeStyleSettingsManager.getSettings(myProject).getTabSize(fileType);
        if (tabSize <= 0) tabSize = 1;
        int nSpaces = tabSize - myColumn % tabSize;
        for (int j = 0; j < nSpaces; j++) {
          writeChar(writer, " ");
        }
      }
      else if (c == '\n' || c == '\r') {
        boolean writeSlashR = false;
        if (c == '\r' && i+1 < start+length && charArray.charAt(i+1) == '\n') {
          writeSlashR = true;
          //noinspection AssignmentToForLoopParameter
          i++;
        }
        else if (c == '\n') {
          writeChar(writer, " ");
        }
        
        LineMarkerInfo marker = getMethodSeparator(i + 1);
        if (marker != null) {
          Color color = marker.separatorColor;
          writer.write("<hr class=\"" + mySeparatorStyles.get(color) + "\">");
        }
        else {
          if (writeSlashR) {
            writeChar(writer, "\r");
          }
          writer.write('\n');
        }
        writeLineNumber(writer);
      }
      else {
        writeChar(writer, String.valueOf(c));
      }
    }
  }

  private void writeChar(Writer writer, String s) throws IOException {
    writer.write(s);
    myColumn++;
  }

  private void writeLineNumber(@NonNls Writer writer) throws IOException {
    myColumn = 0;
    lineCount++;
    if (myPrintLineNumbers) {
      writer.write("<a name=\"l" + lineCount + "\">");

//      String numberCloseTag = writeFontTag(writer, ourLineNumberAttributes);

      writer.write("<span class=\"ln\">");
      String s = Integer.toString(lineCount);
      writer.write(s);
      int extraSpaces = 4 - s.length();
      do {
        writer.write(' ');
      } while (extraSpaces-- > 0);
      writer.write("</span></a>");

//      if (numberCloseTag != null) {
//        writer.write(numberCloseTag);
//      }
    }
  }

  private void writeHeader(@NonNls Writer writer, String title) throws IOException {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    writer.write("<html>\r\n");
    writer.write("<head>\r\n");
    writer.write("<title>" + title + "</title>\r\n");
    writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\r\n");
    writeStyles(writer);
    writer.write("</head>\r\n");
    Color color = scheme.getDefaultBackground();
    if (color==null) color = JBColor.GRAY;
    writer.write("<BODY BGCOLOR=\"#" + Integer.toString(color.getRGB() & 0xFFFFFF, 16) + "\">\r\n");
    writer.write("<TABLE CELLSPACING=0 CELLPADDING=5 COLS=1 WIDTH=\"100%\" BGCOLOR=\"#" + ColorUtil.toHex(new JBColor(Gray.xC0, Gray.x60)) + "\" >\r\n");
    writer.write("<TR><TD><CENTER>\r\n");
    writer.write("<FONT FACE=\"Arial, Helvetica\" COLOR=\"#000000\">\r\n");
    writer.write(title + "</FONT>\r\n");
    writer.write("</center></TD></TR></TABLE>\r\n");
    writer.write("<pre>\r\n");
  }

  private void writeStyles(@NonNls final Writer writer) throws IOException {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    writer.write("<style type=\"text/css\">\n");
    final Color lineNumbers = scheme.getColor(EditorColors.LINE_NUMBERS_COLOR);
    writer.write(String.format(".ln { color: #%s; font-weight: normal; font-style: normal; }\n", ColorUtil.toHex(lineNumbers == null ? Gray.x00 : lineNumbers)));
    HighlighterIterator hIterator = myHighlighter.createIterator(myOffset);
    while(!hIterator.atEnd()) {
      TextAttributes textAttributes = hIterator.getTextAttributes();
      if (!myStyleMap.containsKey(textAttributes)) {
        @NonNls String styleName = "s" + myStyleMap.size();
        myStyleMap.put(textAttributes, styleName);
        writer.write("." + styleName + " { ");
        Color foreColor = textAttributes.getForegroundColor();
        if (foreColor == null) foreColor = scheme.getDefaultForeground();
        writer.write("color: " + colorToHtml(foreColor) + "; ");
        if (BitUtil.isSet(textAttributes.getFontType(), Font.BOLD)) {
          writer.write("font-weight: bold; ");
        }
        if (BitUtil.isSet(textAttributes.getFontType(), Font.ITALIC)) {
          writer.write("font-style: italic; ");
        }
        writer.write("}\n");
      }
      hIterator.advance();
    }
    for (LineMarkerInfo separator : myMethodSeparators) {
      Color color = separator.separatorColor;
      if (color != null && !mySeparatorStyles.containsKey(color)) {
        @NonNls String styleName = "ls" + mySeparatorStyles.size();
        mySeparatorStyles.put(color, styleName);
        String htmlColor = colorToHtml(color);
        writer.write("." + styleName + " { height: 1px; border-width: 0; color: " + htmlColor + "; background-color:" + htmlColor + "}\n");
      }
    }
    writer.write("</style>\n");
  }
  
  private static String colorToHtml(@NotNull Color color) {
    return "rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")";
  }

  private static void writeFooter(@NonNls Writer writer) throws IOException {
    writer.write("</pre>\r\n");
    writer.write("</body>\r\n");
    writer.write("</html>");
  }

  private static boolean equals(TextAttributes attributes1, TextAttributes attributes2) {
    if (attributes2 == null) {
      return attributes1 == null;
    }
    if(attributes1 == null) {
      return false;
    }
    if(!Comparing.equal(attributes1.getForegroundColor(), attributes2.getForegroundColor())) {
      return false;
    }
    if(attributes1.getFontType() != attributes2.getFontType()) {
      return false;
    }
    if(!Comparing.equal(attributes1.getBackgroundColor(), attributes2.getBackgroundColor())) {
      return false;
    }
    if(!Comparing.equal(attributes1.getEffectColor(), attributes2.getEffectColor())) {
      return false;
    }
    return true;
  }

  public String getHTMLFileName() {
    return myHTMLFileName;
  }
}
