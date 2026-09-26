import org.jetbrains.annotations.Nullable;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AssertInstanceOf {
  void test(Object str) {
    assertInstanceOf(String.class, str);
    if(<warning descr="Condition 'str instanceof String' is always 'true'">str instanceof String</warning>) {
    }
    <warning descr="The call to 'assertInstanceOf' always fails, according to its method contracts">assertInstanceOf</warning>(Number.class, str);
  }

  void test2(Object obj) {
    int x;
    assertInstanceOf(String.class, obj, "hello " + (x = (obj instanceof Number ? 1 : 2)));
    if (<warning descr="Condition 'x == 2' is always 'true'">x == 2</warning>) {

    }
  }

  void test3() {
    Object foo = assertInstanceOf(String.class, foo());
    if (<warning descr="Condition 'foo == null' is always 'false'">foo == null</warning>) {
      System.out.println("null!");
    }
  }

  public @Nullable Object foo() {
    return Math.random() > 0.5 ? "foo" : null;
  }
}
