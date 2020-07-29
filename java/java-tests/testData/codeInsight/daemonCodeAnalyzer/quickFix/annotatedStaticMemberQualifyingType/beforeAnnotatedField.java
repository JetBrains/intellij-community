// "Move type annotation" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface Meta { }

class Main {
  public static class Data { }
  static final /*1 */ /*2*/@<caret>/* 3 */ /* 4*/ Meta /*5 *//*6 */ transient Main.Data var = null;
}