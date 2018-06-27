import org.jetbrains.annotations.*;
import java.util.List;

class Test {
  static final Object RES = null;
  final Object xyz = null;
  static final Integer TEST = new Integer(0);

  void test() {
    doSmth(RES);
    doSmth(<weak_warning descr="Value 'xyz' is always 'null'">xyz</weak_warning>);
    System.out.println(process(<weak_warning descr="Value 'xyz' is always 'null'">xyz</weak_warning>).<warning descr="Method invocation 'hashCode' will produce 'java.lang.NullPointerException'">hashCode</warning>());
    Integer x = TEST;
    System.out.println(TEST);
    System.out.println(x);
  }

  @Contract("null -> null")
  native Object process(@Nullable Object obj);

  native void doSmth(@Nullable Object obj);
}