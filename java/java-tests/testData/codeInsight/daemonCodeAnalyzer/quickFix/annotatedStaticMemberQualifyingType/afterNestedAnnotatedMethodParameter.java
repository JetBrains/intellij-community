// "Move type annotation" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface Meta { }

class Main {
  public static class Data {
    public static class Nested { }
  }
  void func(final /*1 */ /*2*/ /*5 *//*6 */ synchronized transient Main.Data.@/* 3 */ /* 4*/ Meta Nested var) { }
}