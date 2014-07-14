import org.jetbrains.annotations.NotNull;

class Test {
  interface I {
    @NotNull
    String get();
  }

  {
    I i = () -> {return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;};
  }
}
