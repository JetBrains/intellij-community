package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;

class TextBlockTransferable implements Transferable {
  private final ReferenceData[] myReferenceDatas;
  private final FoldingData[] myFoldingData;
  private final RawText myRawText;
  private final String myText;

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

  public static class RawText implements Cloneable, Serializable {
    public static final @NonNls DataFlavor FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + RawText.class.getName(),
                                                                   "Raw Text");
    public String rawText;

    public RawText(final String rawText) {
      this.rawText = rawText;
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

  public TextBlockTransferable(String text, ReferenceData[] referenceDatas, FoldingData[] foldingData, RawText rawText) {
    myText = text;
    myReferenceDatas = referenceDatas;
    myFoldingData = foldingData;
    myRawText = rawText;
  }

  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[]{
      DataFlavor.stringFlavor,
      DataFlavor.plainTextFlavor,
      RawText.FLAVOR,
      ReferenceData.FLAVOR,
      FoldingData.FLAVOR
    };
  }

  public boolean isDataFlavorSupported(DataFlavor flavor) {
    DataFlavor[] flavors = getTransferDataFlavors();
    for (DataFlavor flavor1 : flavors) {
      if (flavor.equals(flavor1)) {
        return true;
      }
    }
    return false;
  }

  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (ReferenceData.FLAVOR.equals(flavor)){
      return myReferenceDatas;
    }
    else if (FoldingData.FLAVOR.equals(flavor)){
      return myFoldingData;
    }
    else if (RawText.FLAVOR.equals(flavor)) {
      return myRawText;
    }
    else if (DataFlavor.stringFlavor.equals(flavor)) {
      return myText;
    }
    else if (DataFlavor.plainTextFlavor.equals(flavor)) {
      return new StringReader(myText);
    }
    else {
      throw new UnsupportedFlavorException(flavor);
    }
  }

  public static String convertLineSeparators(String text,
                                             String newSeparator,
                                             ReferenceData[] referenceData,
                                             FoldingData[] foldingData) {
    if (referenceData != null || foldingData != null){
      int size = 0;
      if (referenceData != null){
        size += referenceData.length * 2;
      }
      if (foldingData != null){
        size += foldingData.length * 2;
      }

      int[] offsets = new int[size];
      int index = 0;
      if (referenceData != null){
        for (ReferenceData data : referenceData) {
          offsets[index++] = data.startOffset;
          offsets[index++] = data.endOffset;
        }
      }
      if (foldingData != null){
        for (FoldingData data : foldingData) {
          offsets[index++] = data.startOffset;
          offsets[index++] = data.endOffset;
        }
      }

      text = StringUtil.convertLineSeparators(text, newSeparator, offsets);

      index = 0;
      if (referenceData != null){
        for (ReferenceData data : referenceData) {
          data.startOffset = offsets[index++];
          data.endOffset = offsets[index++];
        }
      }
      if (foldingData != null){
        for (FoldingData data : foldingData) {
          data.startOffset = offsets[index++];
          data.endOffset = offsets[index++];
        }
      }

      return text;
    }
    else{
      return StringUtil.convertLineSeparators(text, newSeparator);
    }
  }
}