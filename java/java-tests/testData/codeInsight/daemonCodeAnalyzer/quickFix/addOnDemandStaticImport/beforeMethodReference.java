// "Add on demand static import for 'java.util.Arrays'" "true"
package test;

import java.util.*;

public class Foo {
  {
    Block<Integer[]> b2 = Arrays::sort;
    Block<Integer> bBroken = Arrays::sort;
    Arra<caret>ys.sort((byte[])null);
  }

  public interface Block<T> {
    void apply(T t);
  }
}