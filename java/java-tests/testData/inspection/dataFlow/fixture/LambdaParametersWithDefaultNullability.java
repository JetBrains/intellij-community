import javax.annotation.*;

@ParametersAreNonnullByDefault 
class Test {
  void foo() {
    Intf i1 = (o) -> {
      if (o == null) {
        System.out.println();
      }
    };
    Intf i2 = (Object o) -> {
      if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {
        System.out.println();
      }
    };
    Intf i3 = (@Nullable Object o) -> {
      if (o == null) {
        System.out.println();
      }
    };
  }
}

interface Intf {
  void foo(Object o);
}