package com.intellij.openapi.editor.ex.util;

public class SegmentArrayWithData extends SegmentArray {
  private short[] myData;

  public SegmentArrayWithData() {
    myData = new short[INITIAL_SIZE];
  }

  public void setElementAt(int i, int startOffset, int endOffset, int data) {
    if (data < 0 && data > Short.MAX_VALUE) throw new IndexOutOfBoundsException("data out of short range" + data);
    super.setElementAt(i, startOffset, endOffset);
    myData = reallocateArray(myData, i+1);
    myData[i] = (short)data;
  }

  public void remove(int startIndex, int endIndex) {
    myData = remove(myData, startIndex, endIndex);
    super.remove(startIndex, endIndex);
  }

  public void replace(int startIndex, int endIndex, SegmentArrayWithData newData) {
    int oldLen = endIndex - startIndex;
    int newLen = newData.getSegmentCount();

    int delta = newLen - oldLen;
    if (delta < 0) {
      remove(endIndex + delta, endIndex);
    }
    else if (delta > 0) {
      SegmentArrayWithData deltaData = new SegmentArrayWithData();
      for (int i = oldLen; i < newLen; i++) {
        deltaData.setElementAt(i - oldLen, newData.getSegmentStart(i), newData.getSegmentEnd(i), newData.getSegmentData(i));
      }
      insert(deltaData, startIndex + oldLen);
    }

    int common = Math.min(newLen, oldLen);
    replace(startIndex, newData, common);
  }


  public void replace(int startOffset, SegmentArrayWithData data, int len) {
    System.arraycopy(data.myData, 0, myData, startOffset, len);
    super.replace(startOffset, data, len);
  }

  public void insert(SegmentArrayWithData segmentArray, int startIndex) {
    myData = insert(myData, segmentArray.myData, startIndex, segmentArray.getSegmentCount());
    super.insert(segmentArray, startIndex);
  }

  public short getSegmentData(int index) {
    if(index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong index: " + index);
    }
    return myData[index];
  }

  public void setSegmentData(int index, int data) {
    if(index < 0 || index >= mySegmentCount) throw new IndexOutOfBoundsException("Wrong index: " + index);
    if (data < 0 && data > Short.MAX_VALUE) throw new IndexOutOfBoundsException("data out of short range" + data);
    myData[index] = (short)data;
  }
}

