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

  public Test5() {
    super();
  }

  public Integer someLength() {
    return something.length();
  }

  public Integer someElseLength() {
    return somethingElse.length();
  }
}

class BadSuper {
  public BadSuper() {
    overrideableMethod();
  }

  protected void overrideableMethod() {}

}
class Test6 extends BadSuper {
  private final String something = new String("something");

  public Test6() {
  }

  public Integer someLength() {
    return something.<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>();
  }

  protected void overrideableMethod() {
    someLength();
  }
}

class Test7 extends BadSuper {
  private final String something = new String("something");

  protected void overrideableMethod() {
    something.<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>();
  }
}

class Test8 {
  // IDEA-186075
  private final Object s = new Object();
  private final Object s2;

  Test8() {
    System.out.println(s.hashCode());
    other();
    s2 = new Object();
  }

  void other() {
    System.out.println(s.hashCode());
    System.out.println(s2.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
  }
}