// "Move type annotation" "true-preview"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface First { }
@Target(ElementType.TYPE_USE)
@interface Second { }

class Main {
  public static class Data { }
  static final /*1 */ /*2*/@<caret>/* 3 */ /* 4*/ First/*one*/ @/*two*/Second /*5 *//*6 */ transient Main.Data var = null;
}