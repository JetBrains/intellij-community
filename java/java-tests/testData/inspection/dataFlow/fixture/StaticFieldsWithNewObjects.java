import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ConstantHolder {
  static final ConstantHolder X = new ConstantHolder();
  static final ConstantHolder Y = new ConstantHolder();

  static final Object[] ARRAY = new Object[10];
  static final Object[] ARRAY2 = new Object[10];

  ConstantHolder() {
    if(X == null) {
      System.out.println("X is initializing");
    }
  }

  interface Foo {
    ConstantHolder A = new ConstantHolder();
    ConstantHolder B = new ConstantHolder();
  }

  @Nullable String str;
}

class TestNewObjects {
  void test(ConstantHolder ti) {
    if(<warning descr="Condition 'ConstantHolder.X == ConstantHolder.Y' is always 'false'">ConstantHolder.X == ConstantHolder.Y</warning>) {
      System.out.println("Impossible");
    }
    if(<warning descr="Condition 'ConstantHolder.Foo.A != ConstantHolder.Foo.B' is always 'true'">ConstantHolder.Foo.A != ConstantHolder.Foo.B</warning>) {
      System.out.println("Always");
    }
    if(<warning descr="Condition 'ti == ConstantHolder.X && ti == ConstantHolder.Y' is always 'false'">ti == ConstantHolder.X && <warning descr="Condition 'ti == ConstantHolder.Y' is always 'false' when reached">ti == ConstantHolder.Y</warning></warning>) {
      System.out.println("Impossible");
    }
    if(<warning descr="Condition 'ti != ConstantHolder.X || ti != ConstantHolder.Foo.A' is always 'true'">ti != ConstantHolder.X || <warning descr="Condition 'ti != ConstantHolder.Foo.A' is always 'true' when reached">ti != ConstantHolder.Foo.A</warning></warning>) {
      System.out.println("Always");
    }
    if(ConstantHolder.X.str != null && ConstantHolder.X.str.isEmpty()) {
      System.out.println("ok");
    }
    if(ConstantHolder.X.str != null && ConstantHolder.Y.str.<warning descr="Method invocation 'isEmpty' may produce 'java.lang.NullPointerException'">isEmpty</warning>()) {
      System.out.println("possible NPE");
    }
  }

  Object getObject() {
    return new Object();
  }

  void testTypes(boolean b) {
    Object x = b ? getObject() : ConstantHolder.X;

    if(x instanceof ConstantHolder && b) {
      System.out.println("true");
    }

    if(!(x instanceof ConstantHolder) && <warning descr="Condition 'b' is always 'true' when reached">b</warning>) {
      System.out.println("false");
    }
  }

  void testArray() {
    if(<warning descr="Condition 'ConstantHolder.ARRAY == ConstantHolder.ARRAY2' is always 'false'">ConstantHolder.ARRAY == ConstantHolder.ARRAY2</warning>) {
      System.out.println("Impossible");
    }
    ConstantHolder.ARRAY[0] = Math.random() > 0.5 ? null : "foo";
    if(ConstantHolder.ARRAY[0] != null) {
      System.out.println(ConstantHolder.ARRAY[0].hashCode());
    }
    if(ConstantHolder.ARRAY2[0] != null) {
      System.out.println(ConstantHolder.ARRAY[0].<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
    }
  }
}