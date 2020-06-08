// "Move type annotation" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface First { }
@Target(ElementType.TYPE_USE)
@interface Second { }

class Main {
  public static class Data {
    public static class Nested { }
  }
  static final /*1 */ /*2*/@/* 3 */ /* 4*/ First/*one*/ @/*two*/<caret>Second /*5 *//*6 */ transient Main.Data.Nested var = null;
}