package com.intellij.formatting;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.TextRange;

import java.io.PrintStream;
import java.util.List;

/**
 * @author yole
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class FormattingModelDumper {
  private FormattingModelDumper() {
  }

  public static void dumpFormattingModel(final ASTBlock block, int indent, final PrintStream output) {
    if (indent == 0) {
      output.println("--- FORMATTING MODEL ---");
    }
    output.print(StringUtil.repeatSymbol(' ', indent));
    List<Block> subBlocks = block.getSubBlocks();
    if (subBlocks.isEmpty()) {
      dumpTextBlock(block, output);
    }
    else {
      dumpBlock(block, output);
      Block prevBlock = null;
      for(Block subBlock: subBlocks) {
        if (prevBlock != null) {
          Spacing spacing = block.getSpacing(prevBlock, subBlock);
          if (spacing != null) {
            dumpSpacing(spacing, indent+2, output);
          }
        }
        prevBlock = subBlock;
        dumpFormattingModel((ASTBlock) subBlock, indent+2, output);
      }
    }
  }

  private static void dumpTextBlock(final ASTBlock block, final PrintStream output) {
    StringBuilder blockData = new StringBuilder("\"" + block.getNode().getText() + "\"");
    dumpBlockProperties(block, blockData);
    output.println(blockData.toString());
  }

  private static void dumpBlock(final ASTBlock block, final PrintStream output) {
    StringBuilder blockData = new StringBuilder("<block ");
    blockData.append(block.getNode().getElementType());
    dumpBlockProperties(block, blockData);
    blockData.append(">");
    output.println(blockData.toString());
  }

  private static void dumpBlockProperties(final ASTBlock block, final StringBuilder blockData) {
    TextRange textRange = block.getTextRange();
    blockData.append(" ").append(textRange.getStartOffset()).append(":").append(textRange.getEndOffset());
    Wrap wrap = block.getWrap();
    if (wrap != null) {
      blockData.append(" ").append(wrap);
    }
    Indent indent = block.getIndent();
    if (indent != null) {
      blockData.append(" ").append(indent);
    }
  }

  private static void dumpSpacing(final Spacing spacing, final int indent, final PrintStream out) {
    out.print(StringUtil.repeatSymbol(' ', indent));
    out.println("spacing: " + spacing);
  }
}
