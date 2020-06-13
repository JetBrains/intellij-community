// "Move type annotation" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface First { }
@Target(ElementType.TYPE_USE)
@interface Second { }
class Generic<T> {}

class Main extends Generic</*1 */ /*2*//*one*/ @/*two*/Second /*5 *//*6 */ Main.Data.@/* 3 */ /* 4*/ First Nested> {
  public static class Data {
    public static class Nested { }
  }
}