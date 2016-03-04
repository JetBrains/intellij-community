import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Super {
  public Super() {
    this(2);
  }

  public Super(int a) {
    staticMethod();
  }
  
  static void staticMethod() {}
  
}

class Test {
  @Nullable
  private static Object getNull() {
    return null;
  }

  private static final Object CONST = getNull();

  public static void test() {
    System.out.println(CONST.<warning descr="Method invocation 'toString' may produce 'java.lang.NullPointerException'">toString</warning>());
  }
}

class Test2 extends Super {
  @NotNull
  private static Object getNotNull() {
    return new Object();
  }

  private static final Object CONST = getNotNull();

  public static void test() {
    System.out.println(CONST.toString());
  }
}

class Test3 {

  private static final Object CONST = "";

  public static void test() {
    System.out.println(CONST.toString());
  }
}

class Test4 {

  public enum Day {
    SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
    THURSDAY, FRIDAY, SATURDAY
  }

  private static final Day CONST = Day.FRIDAY;

  public static void test() {
    System.out.println(CONST.toString());
  }
}

class Test5 {
  private final String something = new String("something");
  private final String somethingElse = "somethingElse";

  public Integer someLength() {
    //May produce nullpointer warning
    return something.length();
  }

  public Integer someElseLength() {
    //No warning
    return somethingElse.length();
  }
}