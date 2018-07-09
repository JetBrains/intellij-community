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
    Integer i = 12;
    if (i == new Integer(12)) {
    }

    <warning descr="Type may be primitive">Integer</warning> i2 = 12;
    if (i2 == 43) {
    }

    Boolean b = true;
    if (b != null) {}
  }

  void varargUse() {
    Integer i = 12;
    vararg(i, i, i, i);
  }

  void vararg(int k, int... i) {

  }
}