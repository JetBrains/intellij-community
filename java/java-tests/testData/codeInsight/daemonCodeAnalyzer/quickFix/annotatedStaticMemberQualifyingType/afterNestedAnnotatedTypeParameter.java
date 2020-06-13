// "Move type annotation" "true"

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@interface Meta { }
class Generic<T> {}

class Main extends Generic</*1 */ /*2*/ /*5 *//*6 */ Main.Data.@/* 3 */ /* 4*/ Meta Nested> {
  public static class Data {
    public static class Nested { }
  }
}