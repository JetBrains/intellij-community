// "Replace with 'isEmpty()'" "true"
package com.siyeh.igtest.style.size_replaceable_by_is_empty;

public class SizeReplaceableByIsEmpty {
  boolean equalsToZero(String s) {
    return s.<caret>length() == 0;
  }
}
