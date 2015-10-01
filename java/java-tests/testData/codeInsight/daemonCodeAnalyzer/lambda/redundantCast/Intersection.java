import java.io.*;
interface Predicate<T> {
  boolean test(String s);
}
class Test {
  private static boolean test(String s) {
    return false;
  }
  
  {
    Predicate<String> mh1 = (Predicate<String> & Serializable)Test::test;
    Predicate<String> mh0 = (<warning descr="Casting 'Test::test' to 'Predicate<String> & Predicate<String>' is redundant">Predicate<String> & <error descr="Repeated interface">Predicate<String></error></warning>)Test::test;
  }
}