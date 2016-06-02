interface A {
  default void fo<caret>o() {
    System.out.println();
  }
}

class B implements A {}