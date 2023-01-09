import org.jetbrains.annotations.*;

public class SwitchExpressionsNullability {
  void cons(@NotNull String str) {}

  void test(@Nullable String a, @Nullable String b, int i, boolean f) {
    cons(((<warning descr="Casting '(switch (i) {...})' to 'String' may produce 'ClassCastException'">String</warning>)(switch(i) {
      case 1 -> <warning descr="Argument 'a' might be null">a</warning>;
      case 2 -> "foo";
      case 3 -> <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>;
      case 4 -> {
        System.out.println("four!");
        yield f ? i : <warning descr="Argument 'b' might be null">b</warning>;
      }
      default -> "bar";
    })));
  }
}
