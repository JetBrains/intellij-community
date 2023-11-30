import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AssertInstanceOf {
  void test(Object str) {
    assertInstanceOf(String.class, str);
    if(<warning descr="Condition 'str instanceof String' is always 'true'">str instanceof String</warning>) {
    }
    <warning descr="The call to 'assertInstanceOf' always fails with an exception">assertInstanceOf</warning>(Number.class, str);
  }

  void test2(Object obj) {
    int x;
    assertInstanceOf(String.class, obj, "hello " + (x = (obj instanceof Number ? 1 : 2)));
    if (<warning descr="Condition 'x == 2' is always 'true'">x == 2</warning>) {

    }
  }
}
