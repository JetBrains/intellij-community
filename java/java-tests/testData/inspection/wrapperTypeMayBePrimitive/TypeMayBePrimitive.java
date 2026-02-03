import java.util.*;

class TypeMayBePrimitive {
  void initializer() {
    <warning descr="Type may be primitive">Boolean</warning> b1 = true;
    use(b1);
    Boolean b2 = null;
    use(b2);
  }

  // While the variable will not be used, we will not show the message
  void use(boolean b) {}

  void assignment() {
    Integer i1 = 12;
    i1 = null;

    Long withoutInitializer;

    Boolean boxNeeded;
    boxNeeded = getNullBox();
    use(boxNeeded);
    <warning descr="Type may be primitive">Boolean</warning> boxNotNeeded;
    boxNotNeeded = getNotNullBox();
    use(boxNotNeeded);
    boxNotNeeded |= true;

    Boolean bool = getBool();
    use(bool);
  }

  Boolean getBool() {
    return true;
  }

  private static Boolean getNullBox() {
    return null;
  }


   private static Boolean getNotNullBox() {
    return true;
  }

  void primitiveParam(int i) {}
  void boxedParam(Integer i) {}
  void boxedAndPrimitiveParam(int i1, Integer i2) {}

  void methods() {
    <warning descr="Type may be primitive">Integer</warning> i1 = 12;
    primitiveParam(i1);

    Integer i2 = 12;
    boxedParam(i2);

    <warning descr="Type may be primitive">Integer</warning> i3 = 12;
    boxedAndPrimitiveParam(i3, i3);
  }

  void constructors() {
    Integer i = getBool() ? 1 : 12;
    C c = new C(i);
  }

  static class C {
    Integer i;

    public C(Integer i) {
      this.i = i;
    }
  }

  void synchronize() {
    Integer i = 12;
    synchronized (i) {

    }
  }

  void binop() {
    <warning descr="Type may be primitive">Integer</warning> i = 12;
    if (i == new Integer(12)) {
    }

    <warning descr="Type may be primitive">Integer</warning> i2 = 12;
    if (i2 == 43) {
    }

    Boolean b = true;
    if (b != null) {}
  }

  void bool() {
    final <warning descr="Type may be primitive">Boolean</warning> b = Boolean.valueOf("true");
    use(b);
  }
}

class StringConcat {
  private static final String PREFIX = "1l";

  public void foo(Queue<String> queue, long rangeStart, long rangeEnd, long step) throws InterruptedException {

    for (<warning descr="Type may be primitive">Long</warning> number = rangeStart; number <= rangeEnd; number += step) {
      queue.put(PREFIX + number);
    }
  }

  static class Queue<S> {
    public void put(String s) {

    }
  }
}

class ValueOf {
  public void foo(long step, String s) {
    <warning descr="Type may be primitive">Long</warning> rangeStart = Long.valueOf("12");
    for (long number = rangeStart; number <= 12L; number += step) {
      System.out.println(number);
    }
  }
}

class TheSameName {
  void foo(int n) {
    switch (n) {
      case 1: {
        <warning descr="Type may be primitive">Integer</warning> i = Integer.valueOf("1");
        use(i);
        break;
      }
      case 2: {
        <warning descr="Type may be primitive">Float</warning> i = Float.valueOf("1");
        use(i);
        break;
      }
    }
  }

  void use(int i) { }
  void use(float f) { }
}

class BoxUnboxBalance {
  void needBox(Object box) {}
  void needPrimitive(long prim) {}

  public long test1() {
    <warning descr="Type may be primitive">Long</warning> l = 12L; // -1 as no prim boxing
    needBox(l); // +1 as boxed param
    return l + 12; // -2 as unbox and result boxing removed
    // Total: -2  ==> remove boxing
  }

  public Long test2() {
    Long l = 12L; // -1 as no prim boxing
    needBox(l); // +1 as boxed param
    return l; // +1 as box of result
    // Total: +1  ==> preserve boxing
  }

  public void loopBoost(int[] arr) {
    <warning descr="Type may be primitive">Long</warning> l = 12L; // -1 as no prim boxing
    needBox(l); // +1 as boxed param
    needBox(l); // +1 as boxed param
    needBox(l); // +1 as boxed param
    for (int ignored : arr) {
      needPrimitive(l); // -1 as parameter must be unboxed, with loop boost -10
    }
    // Total: -8  ==> remove boxing
  }
}
