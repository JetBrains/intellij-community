import java.io.*;
interface Predicate<T> {
  boolean test(String s);
}
interface SerPredicate<T> extends Predicate<T>, Serializable {
}

interface NonSerPredicate<T> extends Predicate<T> {
}

class Test {
  private static boolean test(String s) {
    return false;
  }

  {
    Predicate<String> mh2 = (SerPredicate<String>)Test::test;
    Predicate<String> mh02 = (<warning descr="Casting 'Test::test' to 'NonSerPredicate<String>' is redundant">NonSerPredicate<String></warning>)Test::test;
  }
}