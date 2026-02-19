package com.test3;
import com.test.TestException;
import com.test2.Test;

class X{
  public void test() {
    try {
      Test.test();
    } catch (TestException e) {
        <selection>throw new RuntimeException(e);</selection><caret>
    }
  }
}
