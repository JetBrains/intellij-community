package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

public class FoldingTransferableData implements TextBlockTransferableData, Serializable {
  private FoldingData[] myFoldingDatas;

  public FoldingTransferableData(final FoldingData[] foldingDatas) {
    myFoldingDatas = foldingDatas;
  }

  public DataFlavor getFlavor() {
    return FoldingData.FLAVOR;
  }

  public int getOffsetCount() {
    return myFoldingDatas.length * 2;
  }

  public int getOffsets(final int[] offsets, int index) {
    for (FoldingData data : myFoldingDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  public int setOffsets(final int[] offsets, int index) {
    for (FoldingData data : myFoldingDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  protected FoldingTransferableData clone() {
    FoldingData[] newFoldingData = new FoldingData[myFoldingDatas.length];
    for (int i = 0; i < myFoldingDatas.length; i++) {
      newFoldingData[i] = (FoldingData)myFoldingDatas[i].clone();
    }
    return new FoldingTransferableData(newFoldingData);
  }

  public FoldingData[] getData() {
    return myFoldingDatas;
  }

  public static class FoldingData implements Cloneable, Serializable {
    public static final @NonNls DataFlavor FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + FoldingData.class.getName(),
                                                                   CodeInsightBundle.message("paste.data.flavor.folding"));

    public int startOffset;
    public int endOffset;
    public final boolean isExpanded;

    public FoldingData(int startOffset, int endOffset, boolean expanded){
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      isExpanded = expanded;
    }

    public Object clone() {
      try{
        return super.clone();
      }
      catch(CloneNotSupportedException e){
        throw new RuntimeException();
      }
    }
  }
}
