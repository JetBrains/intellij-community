/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;

import java.io.PrintStream;
import java.util.List;

/**
 * Convenience utilities to print out various formatting blocks information.
 *
 * @author rvishnyakov
 */
public class BlockDebugUtil {

  /**
   * Prints out a dump of nested formatting blocks. 
   * @param out   The output stream.
   * @param block The root block to start with.
   */
  public static void dumpBlockTree(PrintStream out, Block block) {
    out.println("--- BLOCK TREE DUMP ---");
    dumpBlockTree(out, block, "", true);
    out.println("--- END OF DUMP ---\n\n");
  }


  /**
   * Print out a single block info without child blocks.
   * @param out   The output stream.
   * @param block The block to print the info for.
   */
  public static void dumpBlock(PrintStream out, Block block) {
    dumpBlockTree(out, block, "", false);
  }

  private static void dumpBlockTree(PrintStream out, Block block, String indent, boolean withChildren) {
    if (block == null) return;
    out.print(indent + block.getClass().getSimpleName());
    if (block.getIndent() != null) {
      out.print(" " + block.getIndent());
    }
    else {
      out.print(" <NO INDENT>");
    }
    out.print(" " + block.getTextRange() + " ");
    if (block instanceof ASTBlock) {
      ASTNode node = ((ASTBlock)block).getNode();
      if (node != null) {
        out.print(" " + node.getElementType());
        String text = node.getText();
        int eolPos = text.indexOf('\n');
        if (eolPos > 0) {
          text = text.substring(0, eolPos) + "...";
        }
        out.print(" \"" + text + "\"");
      }
    }
    out.println();
    if (withChildren) {
      List<Block> subBlocks = getSubBlocks(block);
      if (subBlocks != null && subBlocks.size() > 0) {
        out.println(indent + "{");
        for (Block child : subBlocks) {
          dumpBlockTree(out, child, indent + "    ", true);
        }
        out.println(indent + "}");
      }
    }
  }

  private static List<Block> getSubBlocks(Block root) {
    return root.getSubBlocks();
  }
}
