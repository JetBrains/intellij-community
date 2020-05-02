
class Conditional {
  void m(Object p, boolean b) {
    int a  = b ? null : ((Getter<Integer>) p).get();
    int a1 = b ? <error descr="Incompatible types. Found: 'null', required: 'int'">null</error> : Conditional.<Integer>f();
    int a2 = b ? null : 1;
    int a3 = b ? null : f1();
    int a4 = b ? <error descr="Incompatible types. Found: 'null', required: 'int'">null</error> : f2();
    int a5 = b ? <error descr="Incompatible types. Found: 'null', required: 'int'">null</error> : Conditional.<Integer, Integer>f2();
    Long someNum = b ? getNum(5L) : <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">0</error>;
  }

  private static <T> T getNum(T num) {
    return num;
  }

  private static <T> T f() {
    return null;
  }

  private static int f1() {
    return 1;
  }

  private static <T extends Integer, S extends T> S f2() {
    return null;
  }
}

interface Getter<A> {
  A get();
}