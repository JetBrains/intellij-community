package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

/**
 * @author yole
 */
public class IndentTransferableData implements TextBlockTransferableData, Serializable {
  private static @NonNls DataFlavor ourFlavor;

  private final int myIndent;
  private final int myFirstLineLeadingSpaces;

  public IndentTransferableData(int indent, int firstLineLeadingSpaces) {
    myIndent = indent;
    myFirstLineLeadingSpaces = firstLineLeadingSpaces;
  }

  public int getIndent() {
    return myIndent;
  }

  public int getFirstLineLeadingSpaces() {
    return myFirstLineLeadingSpaces;
  }

  public DataFlavor getFlavor() {
    return getDataFlavorStatic();
  }

  public static DataFlavor getDataFlavorStatic() {
    if (ourFlavor != null) {
      return ourFlavor;
    }
    try {
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + IndentTransferableData.class.getName(), "Python indent transferable data");
    }
    catch (NoClassDefFoundError e) {
      return null;
    }
    catch (IllegalArgumentException e) {
      return null;
    }
    return ourFlavor;
  }

  public int getOffsetCount() {
    return 0;
  }

  public int getOffsets(int[] offsets, int index) {
    return index;
  }

  public int setOffsets(int[] offsets, int index) {
    return index;
  }

  protected IndentTransferableData clone() {
    return new IndentTransferableData(myIndent, myFirstLineLeadingSpaces);
  }
}
