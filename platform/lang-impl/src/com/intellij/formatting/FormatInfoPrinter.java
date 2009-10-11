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

package com.intellij.formatting;

import org.jdom.Element;

import java.util.List;

public class FormatInfoPrinter {
  private final Block myRootBlock;
  private final FormattingDocumentModel myDocumentModel;


  public FormatInfoPrinter(final Block rootBlock, final FormattingDocumentModel documentModel) {
    myRootBlock = rootBlock;
    myDocumentModel = documentModel;
  }

  public Element blocksAsTree(){
    return createBlockElement(myRootBlock);
  }

  private Element createBlockElement(final Block block) {
    final Element result = new Element("Block");
    printSimpleBlock(block, result);

    result.setAttribute("class", block.getClass().getName());

    if (block.getSubBlocks().size() == 0) {
      result.setAttribute("text", myDocumentModel.getText(block.getTextRange()).toString());
    }

    Wrap wrap = block.getWrap();
    if (wrap != null) {
      Element wrapElem = new Element("Wrap");
      result.addContent(wrapElem);
      WrapImpl wrapImpl = (WrapImpl)wrap;
      wrapElem.setAttribute("id", wrapImpl.getId());
      wrapElem.setAttribute("type", wrapImpl.getType().name());
      wrapElem.setAttribute("ignoreParents", String.valueOf(wrapImpl.getIgnoreParentWraps()));
      wrapElem.setAttribute("wrapFirst", String.valueOf(wrapImpl.isWrapFirstElement()));

      WrapImpl parent = wrapImpl.getParent();
      if (parent != null) {
        wrapElem.setAttribute("parent", parent.getId());
      }
    }
    Indent indent = block.getIndent();
    if (indent != null) {
      Element indentElement = new Element("Indent");
      result.addContent(indentElement);
      indentElement.setAttribute("type", ((IndentImpl)indent).getType().toString());
    }
    AlignmentImpl alignment = (AlignmentImpl)block.getAlignment();
    if (alignment != null) {
      Element alEl = new Element("Alignment");
      result.addContent(alEl);
      alEl.setAttribute("id", alignment.getId());
    }
    final List<Block> subBlocks = block.getSubBlocks();
    for (int i = 0; i < subBlocks.size(); i++) {
      if (i > 0 && i < subBlocks.size() - 1) {
        Spacing spacing = block.getSpacing(subBlocks.get(i), subBlocks.get(i + 1));
        if (spacing != null) {
          result.addContent(createSpacingElement(spacing));
        }
      }
      result.addContent(createBlockElement(subBlocks.get(i)));
    }
    
    return result;
  }

  private Element createSpacingElement(final Spacing spacing) {
    final Element result = new Element("Spacing");
    final SpacingImpl impl = ((SpacingImpl)spacing);

    result.setAttribute("keepBlankLines", String.valueOf(impl.getKeepBlankLines()));
    result.setAttribute("keepLineBreaks", String.valueOf(impl.shouldKeepLineFeeds()));
    result.setAttribute("minspaces", String.valueOf(impl.getMinSpaces()));
    result.setAttribute("maxspaces", String.valueOf(impl.getMaxSpaces()));
    result.setAttribute("minlinefeeds", String.valueOf(impl.getMinLineFeeds()));
    result.setAttribute("readOnly", String.valueOf(impl.isReadOnly()));
    result.setAttribute("safe", String.valueOf(impl.isSafe()));

    return result;
  }

  private void printSimpleBlock(final Block block, Element element) {
    element.setAttribute("start", String.valueOf(block.getTextRange().getStartOffset()));
    element.setAttribute("end", String.valueOf(block.getTextRange().getEndOffset()));
  }
}
