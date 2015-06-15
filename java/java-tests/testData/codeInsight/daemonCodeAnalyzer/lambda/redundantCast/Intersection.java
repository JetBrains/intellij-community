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
    Predicate<String> mh0 = (Predicate<String> & <error descr="Repeated interface">Predicate<String></error>)Test::test;
  }
}