// "Move type annotation" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface Meta { }
class Generic<T> {}

class Main extends Generic</*1 */ /*2*/@<caret>/* 3 */ /* 4*/ Meta /*5 *//*6 */ Main.Data.Nested> {
  public static class Data {
    public static class Nested { }
  }
}