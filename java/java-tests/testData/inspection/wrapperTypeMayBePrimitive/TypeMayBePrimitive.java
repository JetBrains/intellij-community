import java.util.*;

class TypeMayBePrimitive {
  void initializer() {
    <warning descr="Convert wrapper type to primitive">Boolean</warning> b1 = true;
    use(b1);
    Boolean b2 = null;
    use(b2);
  }

  // While the variable will not be used, we will not show the message
  void use(boolean b) {}

  void conditionalExpr(boolean c) {
    Boolean b1 = true;
    boolean res1 = b1 ? true : false;

    Boolean b2 = false;
    boolean res2 = c ? b2 : false;
  }

  void assignment() {
    Integer i1 = 12;
    i1 = null;

    Long withoutInitializer;

    Boolean boxNeeded = getNullBox();
    use(boxNeeded);
    <warning descr="Convert wrapper type to primitive">Boolean</warning> boxNotNeeded = getNotNullBox();
    use(boxNotNeeded);
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
    <warning descr="Convert wrapper type to primitive">Integer</warning> i1 = 12;
    primitiveParam(i1);

    Integer i2 = 12;
    boxedParam(i2);

    Integer i3 = 12;
    boxedAndPrimitiveParam(i3, i3);
  }
}