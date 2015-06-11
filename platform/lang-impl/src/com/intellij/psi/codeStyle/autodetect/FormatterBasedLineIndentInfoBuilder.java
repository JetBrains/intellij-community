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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.formatting.*;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.formatter.common.NewLineBlocksIterator;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FormatterBasedLineIndentInfoBuilder {
  private static final int MAX_NEW_LINE_BLOCKS_TO_PROCESS = 500;

  private final PsiFile myFile;
  private final Document myDocument;
  private final CharSequence myText;
  private final CodeStyleSettings mySettings;
  private final FormattingModelBuilder myFormattingModelBuilder;

  public FormatterBasedLineIndentInfoBuilder(@NotNull PsiFile file) {
    Project project = file.getProject();

    myFile = file;
    myDocument = PsiDocumentManager.getInstance(project).getDocument(file);
    myText = myDocument != null ? myDocument.getCharsSequence() : null;
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myFormattingModelBuilder = LanguageFormatting.INSTANCE.forContext(myFile);
  }

  public List<LineIndentInfo> build() {
    if (myText == null || myFormattingModelBuilder == null) return null;

    List<Block> normallyIndentedBlocks = ContainerUtil.filter(getBlocksStartingNewLine(), new Condition<Block>() {
      @Override
      public boolean value(Block block) {
        Indent.Type type = block.getIndent() instanceof IndentImpl ? ((IndentImpl)block.getIndent()).getType() : null;
        return type == Indent.Type.NONE || type == Indent.Type.NORMAL;
      }
    });

    return ContainerUtil.map(normallyIndentedBlocks, new Function<Block, LineIndentInfo>() {
      @Override
      public LineIndentInfo fun(Block newLineBlock) {
        int blockStartOffset = newLineBlock.getTextRange().getStartOffset();
        int lineStartOffset = myDocument.getLineStartOffset(myDocument.getLineNumber(blockStartOffset));
        return createLineIndentInfo(lineStartOffset, blockStartOffset);
      }
    });
  }

  @NotNull
  private List<Block> getBlocksStartingNewLine() {
    FormattingModel model = myFormattingModelBuilder.createModel(myFile, mySettings);
    Block root = model.getRootBlock();
    NewLineBlocksIterator newLineBlocksIterator = new NewLineBlocksIterator(root, myDocument);

    List<Block> newLineBlocks = new ArrayList<Block>();
    int currentLine = 0;
    while (newLineBlocksIterator.hasNext() && currentLine < MAX_NEW_LINE_BLOCKS_TO_PROCESS) {
      Block next = newLineBlocksIterator.next();
      newLineBlocks.add(next);
      currentLine++;
    }

    return newLineBlocks;
  }

  @NotNull
  private LineIndentInfo createLineIndentInfo(int lineStartOffset, int textStartOffset) {
    if (CharArrayUtil.indexOf(myText, "\t", lineStartOffset, textStartOffset) > 0) {
      return LineIndentInfo.LINE_WITH_TABS;
    }
    return LineIndentInfo.newWhiteSpaceIndent(textStartOffset - lineStartOffset);
  }

}
