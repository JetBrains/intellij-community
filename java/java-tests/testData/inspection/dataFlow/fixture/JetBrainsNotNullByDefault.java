import org.jetbrains.annotations.*;
import java.util.List;
import java.util.Random;

@NotNullByDefault
class FromDemo {
  native <T extends Number> T get();

  public void test() {
    Object o = new FromDemo().get();
    if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {
      System.out.println("1");
    }
  }

  native <T extends Number> T get2();

  public void test2() {
    Object o = new FromDemo().get2();
    if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {
      System.out.println("1");
    }
  }

  <T extends @Nullable Object> T oneOfTwo(T t1, T t2) {
    return new Random().nextBoolean() ? t1 : t2;
  }

  public void test(@Nullable Integer t1, @Nullable Integer t2) {
    Integer o = new FromDemo().oneOfTwo(t1, t2);
    // TODO: should not warn
    if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {
      System.out.println("1");
    }
  }

  public void test2(Object t1, Object t2) {
    Object o = new FromDemo().oneOfTwo(t1, t2);
    if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {
      System.out.println("1");
    }
  }
}

@NotNullByDefault
public class JetBrainsNotNullByDefault {
  String field;

  String test(String param) {
    if (<warning descr="Condition 'param == null' is always 'false'">param == null</warning>) {}
    if (<warning descr="Condition 'field == null' is always 'false'">field == null</warning>) {}
    String local = System.getProperty("a");
    if (local == null) {}
    return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
  }

  <T> T generic(T param) {
    if (<warning descr="Condition 'param == null' is always 'false'">param == null</warning>) {
      return param;
    }
    return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
  }

  <T extends Object> T generic2(T param) {
    if (<warning descr="Condition 'param == null' is always 'false'">param == null</warning>) {
      return param;
    }
    return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
  }

  <T extends @UnknownNullability Object> T generic3(T param) {
    if (param == null) {
      return <warning descr="'null' is returned by the method which is not declared as @Nullable">param</warning>;
    }
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }

  <T> List<T> genericList(List<T> param) {
    for (T t : param) {
      if (<warning descr="Condition 't == null' is always 'false'">t == null</warning>) {
        return null;
      }
    }
    return param;
  }

  <T extends @UnknownNullability Object> List<T> genericList2(List<T> param) {
    for (T t : param) {
      if (t == null) {
        return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
      }
    }
    return param;
  }

  void use2(String s) {
    // T is inferred as String from "hello" type
    if (generic3("hello") == null) {}
    // T is inferred as @NotNull String from the `s` type
    if (<warning descr="Condition 'generic3(s) == null' is always 'false'">generic3(s) == null</warning>) {}
  }

  void use(List<String> list) {
    // T is inferred as @NotNull String from the `list` type
    for (String s : genericList(list)) {
      if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
    }
  }

  static class StaticInner implements NullableMember {
    public String myGet() {
      return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
    }

    @Override
    public String get() {
      return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
    }
  }
}

interface NullableMember {
  @Nullable String get();
}

@NotNullByDefault
class Test<K> {
  String field;

  K test(String param) {
    if (<warning descr="Condition 'param == null' is always 'false'">param == null</warning>) {
    }
    if (<warning descr="Condition 'field == null' is always 'false'">field == null</warning>) {
    }
    String local = System.getProperty("a");
    if (local == null) {
    }
    return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
  }
}

@NotNullByDefault
class Test2{
  public static void main(String[] args) {
    final Test<String> stringTest = new Test<>();
    final String test = stringTest.test(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    if(<warning descr="Condition 'test != null' is always 'true'">test != null</warning>) {
      System.out.println("1");
    }
    // TODO: should report incompatible bound @Nullable String = K extends @NotNullByDefault Object
    final Test<@Nullable String> stringTest2 = new Test<>();
    final String test2 = stringTest2.test(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    if(<warning descr="Condition 'test2 != null' is always 'true'">test2 != null</warning>) {
      System.out.println("1");
    }
  }
}

class InheritNotNullByDefault {
  static class StaticInner implements NullableMember {

    @Override
    public String get(String s) {
      if(<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {
        return null;

      }
      return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;

    }

    public static void main(String[] args) {
      final StaticInner staticInner = new StaticInner();
      final String s = staticInner.get("1");
      if(<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {
        System.out.println("null");
      }
      final String s1 = staticInner.get(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    }
  }

  @NotNullByDefault
  interface NullableMember {
    String get(String s);
  }
}
