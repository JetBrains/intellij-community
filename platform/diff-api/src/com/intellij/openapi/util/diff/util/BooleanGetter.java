package com.intellij.openapi.util.diff.util;

public interface BooleanGetter {
  BooleanGetter TRUE = new BooleanGetter() {
    @Override
    public boolean get() {
      return true;
    }
  };

  boolean get();
}
