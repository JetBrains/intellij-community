// "Create method 'transform'" "true-preview"

interface X {
  default void x() {
    String s = <caret>transform("skulduggery");
  }
}