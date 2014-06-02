import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class Foo {

  @Contract("_,null->null;_,!null->!null")
  static Object f(@NotNull Object o1, Object o2) {
    return o2;
  }

  static Object g(Object o1) {
    return f(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, o1);
  }

}

