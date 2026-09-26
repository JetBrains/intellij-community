// "Create method 'transform'" "true-preview"

interface X {
  default void x() {
    String s = transform("skulduggery");
  }

    String transform(String skulduggery);
}