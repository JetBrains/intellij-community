// "Add on demand static import for 'java.util.Arrays'" "true"
package test;

import java.util.*;

import static java.util.Arrays.*;

public class Foo {
  {
    Block<Integer[]> b2 = Arrays::sort;
    sort((byte[])null);
  }

  public interface Block<T> {
    void apply(T t);
  }
}