import java.io.*;
interface Predicate<T> {
  boolean test(String s);
}
interface I {}
class Test {
  private static boolean test(String s) {
    return false;
  }
  
  {
    Predicate<String> mh1 = (Predicate<String> & Serializable)Test::test;
    Predicate<String> mh0 = (I & Predicate<String>) (<warning descr="Casting 'Test::test' to 'Predicate<String>' is redundant">Predicate<String></warning>)Test::test;
  }
}