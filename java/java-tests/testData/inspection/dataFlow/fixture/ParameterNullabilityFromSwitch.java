import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class Test {
  static void nullable(String s) {
    switch (s) {
      case "xyz" -> System.out.println("xyz");
      case null, default -> System.out.println("else");
    }
  }

  static void notNullable(String s) {
    switch (s) {
      case "xyz" -> System.out.println("xyz");
      case default -> System.out.println("else");
    }
  }

  public static void main(String[] args) {
    nullable(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
    notNullable(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
}