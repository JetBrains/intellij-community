package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

/**
 * @author yole
 */
public class ReferenceTransferableData implements TextBlockTransferableData, Cloneable {
  private ReferenceData[] myReferenceDatas;

  public ReferenceTransferableData(final ReferenceData[] referenceDatas) {
    myReferenceDatas = referenceDatas;
  }

  public DataFlavor getFlavor() {
    return ReferenceData.FLAVOR;
  }

  public int getOffsetCount() {
    return myReferenceDatas.length * 2;
  }

  public int getOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  public int setOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  public ReferenceTransferableData clone() {
    ReferenceData[] newReferenceData = new ReferenceData[myReferenceDatas.length];
    for (int i = 0; i < myReferenceDatas.length; i++) {
      newReferenceData[i] = (ReferenceData)myReferenceDatas[i].clone();
    }
    return new ReferenceTransferableData(newReferenceData);
  }

  public ReferenceData[] getData() {
    return myReferenceDatas;
  }

  public static class ReferenceData implements Cloneable, Serializable {
    public static final @NonNls DataFlavor FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType+";class="+ReferenceData.class.getName(),
                                                                   CodeInsightBundle.message("paste.dataflavor.referencedata"));

    public int startOffset;
    public int endOffset;
    public final String qClassName;
    public final String staticMemberName;

    public ReferenceData(int startOffset, int endOffset, String qClassName, String staticMemberDescriptor) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.qClassName = qClassName;
      this.staticMemberName = staticMemberDescriptor;
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
