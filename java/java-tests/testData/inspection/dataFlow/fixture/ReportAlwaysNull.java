import org.jetbrains.annotations.*;
import java.util.List;
import java.util.Collections;

class Test {
  static final Object RES = null;
  final Object xyz = null;
  static final Integer TEST = new Integer(0);

  void test() {
    doSmth(RES);
    doSmth(<weak_warning descr="Value 'xyz' is always 'null'">xyz</weak_warning>);
    System.out.println(process(<weak_warning descr="Value 'xyz' is always 'null'">xyz</weak_warning>).<warning descr="Method invocation 'hashCode' will produce 'NullPointerException'">hashCode</warning>());
    Integer x = TEST;
    System.out.println(TEST);
    System.out.println(x);
    System.out.println((String)null);
    boolean[] f = new boolean[1];
    f[0] = true;
    Object smth = getSmth();
    Object[] smths = smth == null ? null : new Object[] {smth};
    List<?> list = smth == null ? null : Collections.singletonList(smth);
  }

  void parens() {
    Object x = null;
    doNotNull(<warning descr="Passing 'null' argument to parameter annotated as @NotNull"><weak_warning descr="Value 'x' is always 'null'">x</weak_warning></warning>);
    x = null;
    doNotNull((<warning descr="Passing 'null' argument to parameter annotated as @NotNull"><weak_warning descr="Value 'x' is always 'null'">x</weak_warning></warning>));
  }

  @NotNull Object testReturn(Object x1, Object x2) {
    if(x1 == null) return <warning descr="'null' is returned by the method declared as @NotNull"><weak_warning descr="Value 'x1' is always 'null'">x1</weak_warning></warning>;
    if(x2 == null) return (<warning descr="'null' is returned by the method declared as @NotNull"><weak_warning descr="Value 'x2' is always 'null'">x2</weak_warning></warning>);
    return new Object();
  }

  @Nullable
  native Object getSmth();

  @Contract("null -> null")
  native Object process(@Nullable Object obj);

  native void doNotNull(@NotNull Object obj);

  native void doSmth(@Nullable Object obj);

  void bar(Void p) {
    System.out.println(p);
  }
}