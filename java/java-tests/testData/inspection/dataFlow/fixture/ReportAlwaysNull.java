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

  @Nullable
  native Object getSmth();

  @Contract("null -> null")
  native Object process(@Nullable Object obj);

  native void doSmth(@Nullable Object obj);

  void bar(Void p) {
    System.out.println(p);
  }
}