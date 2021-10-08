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

  void nullableDayWithNullLabel(@Nullable Day d) {
    switch (d) {
      case MONDAY:
        break;
      case TUESDAY:
        break;
      case WEDNESDAY:
        break;
      case null:
        break;
    }
  }

  void nullableDayWithoutNullLabel(@Nullable Day d) {
    switch (<warning descr="Dereference of 'd' may produce 'NullPointerException'">d</warning>) {
      case MONDAY:
        break;
      case TUESDAY:
        break;
      case WEDNESDAY:
        break;
    }
  }

  void unknownDayWithNullLabel(Day d) {
    switch (d) {
      case MONDAY:
        break;
      case TUESDAY:
        break;
      case WEDNESDAY:
        break;
      case null:
        break;
    }
  }

  void unknownDayWithoutNullLabel(Day d) {
    switch (d) {
      case MONDAY:
        break;
      case TUESDAY:
        break;
      case WEDNESDAY:
        break;
    }
  }

  void notNullDayWithNullLabel(@NotNull Day d) {
    switch (d) {
      case MONDAY, TUESDAY, WEDNESDAY:
        break;
      case <warning descr="Switch label 'null' is unreachable">null</warning>:
        break;
    }
  }
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}
