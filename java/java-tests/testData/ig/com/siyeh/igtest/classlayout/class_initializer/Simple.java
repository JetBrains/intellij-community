package com.siyeh.igtest.classlayout.class_initializer;

public class Simple {

  <warning descr="Non-'static' initializer">{</warning>}

  Simple() {}

  class Inner {
    <warning descr="Non-'static' initializer"><caret>{</warning>
      System.out.println("");
    }
  }
}