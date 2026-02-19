import java.util.Objects;
import java.util.concurrent.TimeUnit;

class X {
  static final char MY_CHAR = 'x';
  
  void test(int x, int y) {
    System.out.println(Math.<warning descr="Arguments of 'max' are the same. Calling this method with the same arguments is meaningless">max</warning>(x, x));
    int z = x;
    System.out.println(Math.<warning descr="Arguments of 'min' are the same. Calling this method with the same arguments is meaningless">min</warning>(x, z));
    String s = "foo";
    String t = "foo";
    System.out.println("foobar".<warning descr="Arguments of 'replace' are the same. Calling this method with the same arguments is meaningless">replace</warning>(s, t));
    System.out.println("foobar".<warning descr="Arguments of 'replace' are the same. Calling this method with the same arguments is meaningless">replace</warning>('x', 'x'));
    System.out.println("foobar".replace(MY_CHAR, 'x'));
  }

  int testCondition(int a) {
    if(a == 100) return Math.<warning descr="Arguments of 'max' are the same. Calling this method with the same arguments is meaningless">max</warning>(a, 100);
    return 0;
  }

  public static int foo(int x) {
    return Math.<warning descr="Result of 'min' is the same as the first argument making the call meaningless">min</warning>(x, Integer.MAX_VALUE) +
                                                                                                                                                    Math.<warning descr="Result of 'max' is the same as the second argument making the call meaningless">max</warning>(x, Integer.MAX_VALUE);
  }

  int clamp(int x) {
    return Math.<warning descr="Result of 'min' is the same as the first argument making the call meaningless">min</warning>(1, Math.max(x, 100));
  }

  void maxGreater(int x, int y) {
    if (x < y) return;
    System.out.println(Math.<warning descr="Result of 'max' is the same as the first argument making the call meaningless">max</warning>(x, y));
    System.out.println(Math.<warning descr="Result of 'min' is the same as the second argument making the call meaningless">min</warning>(x, y));
  }

  int constants() {
    final int SIZE1 = 10;
    final int SIZE2 = 5;
    return Math.max(1, SIZE1/SIZE2);
  }
  
  void notEquals(int x, int y) {
    if (x == y) return;
    System.out.println(Math.max(x, y));
    System.out.println(Math.min(x, y));
  }

  void objectsRequireNonNull() {
    String s = null;
    String result = Objects.<warning descr="Result of 'requireNonNullElse' is the same as the second argument making the call meaningless">requireNonNullElse</warning>(s, "hello");
    String result2 = Objects.<warning descr="Result of 'requireNonNullElse' is the same as the first argument making the call meaningless">requireNonNullElse</warning>(result, "other");
    Integer val = 123;
    Object res = Objects.<warning descr="Result of 'requireNonNullElse' is the same as the first argument making the call meaningless">requireNonNullElse</warning>(val, 456);
    Object r = Objects.<warning descr="Result of 'requireNonNullElse' is the same as the second argument making the call meaningless">requireNonNullElse</warning>(null, "oops");
  }
  
  void testTimeUnit(TimeUnit tu, long duration, TimeUnit tu2) {
    System.out.println(TimeUnit.MICROSECONDS.<warning descr="Result of 'convert' is the same as the first argument making the call meaningless">convert</warning>(duration, TimeUnit.MICROSECONDS));
    System.out.println(TimeUnit.MICROSECONDS.convert(duration, TimeUnit.MILLISECONDS));
    System.out.println(tu2.convert(duration, tu));
    System.out.println(tu.convert(duration, tu2));
    System.out.println(tu.<warning descr="Result of 'convert' is the same as the first argument making the call meaningless">convert</warning>(duration, tu));
    if (tu2 == tu) {
      System.out.println(tu.<warning descr="Result of 'convert' is the same as the first argument making the call meaningless">convert</warning>(duration, tu2));
    }
  }
}