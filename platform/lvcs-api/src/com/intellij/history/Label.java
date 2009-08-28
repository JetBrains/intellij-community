package com.intellij.history;

public interface Label {
  Label NULL_INSTANCE = new Label() {
    public ByteContent getByteContent(String path) {
      return null;
    }
  };

  ByteContent getByteContent(String path);
}
