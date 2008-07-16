package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;

import java.io.PrintStream;
import java.util.List;

/**
 * @author yole
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class FormattingModelDumper {
  private FormattingModelDumper() {
  }

  public static void dumpFormattingModel(final Block block, int indent, final PrintStream output) {
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
        dumpFormattingModel(subBlock, indent+2, output);
      }
    }
  }

  private static void dumpTextBlock(final Block block, final PrintStream output) {
    StringBuilder blockData = new StringBuilder("\"" + getBlockText(block) + "\"");
    dumpBlockProperties(block, blockData);
    output.println(blockData.toString());
  }

  private static String getBlockText(final Block block) {
    if (block instanceof ASTBlock) {
      return ((ASTBlock)block).getNode().getText();
    }
    else {
      return "unknown block " + block.getClass();
    }
  }

  private static void dumpBlock(final Block block, final PrintStream output) {
    StringBuilder blockData = new StringBuilder("<block ");
    if (block instanceof ASTBlock) {
      blockData.append(((ASTBlock)block).getNode().getElementType());
    }
    dumpBlockProperties(block, blockData);
    blockData.append(">");
    output.println(blockData.toString());
  }

  private static void dumpBlockProperties(final Block block, final StringBuilder blockData) {
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
