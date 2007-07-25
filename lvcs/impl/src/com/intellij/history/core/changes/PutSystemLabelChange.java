package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class PutSystemLabelChange extends PutLabelChange {
  private int myColor;

  public PutSystemLabelChange(String name, int color, long timestamp) {
    super(name, timestamp);
    myColor = color;
  }

  public PutSystemLabelChange(Stream s) throws IOException {
    super(s);
    myColor = s.readInteger();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myColor);
  }

  @Override
  public boolean isSystemLabel() {
    return true;
  }

  public int getColor() {
    return myColor;
  }
}