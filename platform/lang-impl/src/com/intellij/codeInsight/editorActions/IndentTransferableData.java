package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

/**
 * @author yole
 */
public class IndentTransferableData implements TextBlockTransferableData, Serializable {
  private static @NonNls DataFlavor ourFlavor;

  private final int myOffset;

  public IndentTransferableData(int offset) {
    myOffset = offset;
  }


  public int getOffset() {
    return myOffset;
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
    return new IndentTransferableData(myOffset);
  }
}
