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

package com.intellij.psi.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lesya
 */
public class DocumentBasedFormattingModel implements FormattingModel {
  private final Block myRootBlock;
  private final FormattingDocumentModel myDocumentModel;
  private final Document myDocument;
  private final Project myProject;
  private final CodeStyleSettings mySettings;
  private final FileType myFileType;

  public DocumentBasedFormattingModel(final Block rootBlock,
                                      final Document document,
                                      final Project project,
                                      final CodeStyleSettings settings,
                                      final FileType fileType,
                                      final PsiFile file) {
    myRootBlock = rootBlock;
    myDocument = document;
    myProject = project;
    mySettings = settings;
    myFileType = fileType;
    myDocumentModel = new FormattingDocumentModelImpl(document,file);
  }

  public DocumentBasedFormattingModel(final Block rootBlock,
                                      final Project project,
                                      final CodeStyleSettings settings,
                                      final FileType fileType,
                                      final PsiFile file) {
    myRootBlock = rootBlock;
    myProject = project;
    mySettings = settings;
    myFileType = fileType;
    myDocumentModel = FormattingDocumentModelImpl.createOn(file);
    myDocument = myDocumentModel.getDocument();
  }

  @NotNull
  public Block getRootBlock() {
    return myRootBlock;
  }

  @NotNull
  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    boolean removesStartMarker;
    String marker;

    // When processing injection in cdata / comment we need not remove start / end markers that present as whitespace during check in
    // com.intellij.formatting.WhiteSpace and during building formatter model = blocks in e.g. com.intellij.psi.formatter.xml.XmlTagBlock
    if ((removesStartMarker = removesPattern(textRange, whiteSpace, marker = "<![CDATA[") ||
        removesPattern(textRange, whiteSpace, marker ="<!--[")) ||
        removesPattern(textRange, whiteSpace, marker = "]]>") ||
        removesPattern(textRange, whiteSpace, marker = "]-->")
      ) {
      String newWs = null;

      if (removesStartMarker) {    // TODO once we reformat comments we will need to handle their markers as well
        int at = CharArrayUtil.indexOf(myDocument.getCharsSequence(), marker, textRange.getStartOffset(), textRange.getEndOffset() + 1);
        String ws = myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset()).toString();
        newWs = mergeWsWithCdataMarker(whiteSpace, ws, at - textRange.getStartOffset());

        if (removesPattern(textRange, newWs != null ? newWs: whiteSpace, marker = "]]>")) {
          int i;
          if (newWs != null && (i = newWs.lastIndexOf('\n')) > 0) {
            int cdataStart = newWs.indexOf("<![CDATA[");
            int i2 = newWs.lastIndexOf('\n', cdataStart);
            String cdataIndent = i2 != -1 ? newWs.substring(i2 + 1, cdataStart):"";
            newWs = newWs.substring(0, i) + cdataIndent + marker + newWs.substring(i);
          }
        }
      }

      if (newWs == null) return textRange;
      whiteSpace = newWs;
    }

    myDocument.replaceString(textRange.getStartOffset(),
                           textRange.getEndOffset(),
                           whiteSpace);

    return new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + whiteSpace.length());
  }

  private boolean removesPattern(final TextRange textRange, final String whiteSpace, final String pattern) {
    return CharArrayUtil.indexOf(myDocument.getCharsSequence(), pattern, textRange.getStartOffset(), textRange.getEndOffset() + 1) >= 0 &&
        CharArrayUtil.indexOf(whiteSpace, pattern, 0) < 0;
  }

  public TextRange shiftIndentInsideRange(TextRange range, int indent) {
    final int newLength = shiftIndentInside(range, indent);
    return new TextRange(range.getStartOffset(), range.getStartOffset() + newLength);
  }

  public void commitChanges() {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
  }

  private int shiftIndentInside(final TextRange elementRange, final int shift) {
    final StringBuffer buffer = new StringBuffer();
    StringBuffer afterWhiteSpace = new StringBuffer();
    int whiteSpaceLength = 0;
    boolean insideWhiteSpace = true;
    int line = 0;
    for (int i = elementRange.getStartOffset(); i < elementRange.getEndOffset(); i++) {
      final char c = myDocument.getCharsSequence().charAt(i);
      switch (c) {
        case '\n':
          if (line > 0) {
            createWhiteSpace(whiteSpaceLength + shift, buffer);
          }
          buffer.append(afterWhiteSpace.toString());
          insideWhiteSpace = true;
          whiteSpaceLength = 0;
          afterWhiteSpace = new StringBuffer();
          buffer.append(c);
          line++;
          break;
        case ' ':
          if (insideWhiteSpace) {
            whiteSpaceLength += 1;
          }
          else {
            afterWhiteSpace.append(c);
          }
          break;
        case '\t':
          if (insideWhiteSpace) {
            whiteSpaceLength += getIndentOptions().TAB_SIZE;
          }
          else {
            afterWhiteSpace.append(c);
          }

          break;
        default:
          insideWhiteSpace = false;
          afterWhiteSpace.append(c);
      }
    }
    if (line > 0) {
      createWhiteSpace(whiteSpaceLength + shift, buffer);
    }
    buffer.append(afterWhiteSpace.toString());
    myDocument.replaceString(elementRange.getStartOffset(), elementRange.getEndOffset(), buffer.toString());
    return buffer.length();
  }

  private void createWhiteSpace(final int whiteSpaceLength, StringBuffer buffer) {
    if (whiteSpaceLength < 0) return;
    final CodeStyleSettings.IndentOptions indentOptions = getIndentOptions();
    if (indentOptions.USE_TAB_CHARACTER) {
      int tabs = whiteSpaceLength / indentOptions.TAB_SIZE;
      int spaces = whiteSpaceLength - tabs * indentOptions.TAB_SIZE;
      StringUtil.repeatSymbol(buffer, '\t', tabs);
      StringUtil.repeatSymbol(buffer, ' ', spaces);
    }
    else {
      StringUtil.repeatSymbol(buffer, ' ', whiteSpaceLength);
    }
  }

  private CodeStyleSettings.IndentOptions getIndentOptions() {
    return mySettings.getIndentOptions(myFileType);
  }

  public Document getDocument() {
    return myDocument;
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public static String mergeWsWithCdataMarker(String whiteSpace, final String s, final int cdataPos) {
    final int firstCrInGeneratedWs = whiteSpace.indexOf('\n');
    final int secondCrInGeneratedWs = firstCrInGeneratedWs != -1 ? whiteSpace.indexOf('\n', firstCrInGeneratedWs + 1) : -1;
    final int firstCrInPreviousWs = s.indexOf('\n');
    final int secondCrInPreviousWs = firstCrInPreviousWs != -1 ? s.indexOf('\n', firstCrInPreviousWs + 1) : -1;

    boolean knowHowToModifyCData = false;

    if (secondCrInPreviousWs != -1 && secondCrInGeneratedWs != -1 && cdataPos > firstCrInPreviousWs && cdataPos < secondCrInPreviousWs) {
      whiteSpace = whiteSpace.substring(0, secondCrInGeneratedWs) +
                   s.substring(firstCrInPreviousWs + 1, secondCrInPreviousWs) +
                   whiteSpace.substring(secondCrInGeneratedWs);
      knowHowToModifyCData = true;
    }
    if (!knowHowToModifyCData) whiteSpace = null;
    return whiteSpace;
  }
}
