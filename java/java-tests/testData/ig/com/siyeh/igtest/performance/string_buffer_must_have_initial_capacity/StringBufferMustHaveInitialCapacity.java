package com.siyeh.igtest.performance.string_buffer_must_have_initial_capacity;

class StringBufferMustHaveInitialCapacity {

  void m() {
    new <warning descr="'new StringBuffer()' without initial capacity">StringBuffer</warning>();
    new StringBuffer(3);
    new StringBuffer("foo");
    new <warning descr="'new StringBuilder()' without initial capacity">StringBuilder</warning>();
    new StringBuilder(3);
    new StringBuilder("foo");  }

}