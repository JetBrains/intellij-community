package classlayout.class_initializer;

class Java16 {
  class Inner {
    <warning descr="Non-'static' initializer"><caret>{</warning>
      System.out.println("");
    }
  }
}