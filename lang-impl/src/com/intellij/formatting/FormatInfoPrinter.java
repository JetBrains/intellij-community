/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
    final Element result = new Element(getBlockElementName(block));
    printSimpleBlock(block, result);
    final List<Block> subBlocks = block.getSubBlocks();
    for (int i = 0; i < subBlocks.size(); i++) {
      if (i > 0 && i < subBlocks.size() - 1) {
        result.addContent(createSpacingElement(block.getSpacing(subBlocks.get(i), subBlocks.get(i+1))));        
      }
      result.addContent(createBlockElement(subBlocks.get(i)));
    }
    
    return result;
  }

  private Element createSpacingElement(final Spacing spacing) {
    final Element result = new Element("Spacing");
    final SpacingImpl impl = ((SpacingImpl)spacing);
    return result;
  }

  private String getBlockElementName(final Block block) {
    return block.getSubBlocks().size() > 0 ? "CompositeBlock" : "LeafBlock";
  }

  private void printSimpleBlock(final Block block, Element element) {
    if (block.getSubBlocks().size() == 0) {
      element.setAttribute("text", myDocumentModel.getText(block.getTextRange()).toString());
    }
    
    element.setAttribute("wrap", wrapAsString(block.getWrap()));
    if (block.getAlignment() != null) {
      element.setAttribute("align", ((AlignmentImpl)block.getAlignment()).getId());
    }
  }

  private String wrapAsString(final Wrap wrap) {
    if (wrap == null) return "null";
    final WrapImpl impl = ((WrapImpl)wrap);
    StringBuffer result = new StringBuffer();
    result.append(impl.getType().toString()).append(" ").append(impl.getId());
    if (impl.isWrapFirstElement()) {
      result.append(" wrapFirst");
    }
    if (impl.getIgnoreParentWraps()) {
      result.append(" ignoreParents");
    }
    return result.toString();
  }
}
