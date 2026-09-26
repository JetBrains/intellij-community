import org.junit.Assert;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Simple {

  public static final int EXPECTED = -9;
  private static final Map<String, Integer> map = new HashMap();

  private class BeanCreator {

    public BeanCreator withQuery(int s, Map test, TimeUnit timeUnit) {
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(s, -1);
      Assert.<warning descr="Arguments to 'assertSame()' in wrong order">assertSame</warning>(s, EXPECTED);
      junit.framework.Assert.<warning descr="Arguments to 'failNotEquals()' in wrong order">failNotEquals</warning>("asdfasd", s, EXPECTED);

      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(timeUnit, TimeUnit.HOURS);
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(test, map);

      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>("message", test, null);
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(new X().m(), new Integer(10));
      return null;
    }
  }

  enum TimeUnit {
    HOURS
  }

  class X {
    Integer m() {
      return null;
    }
  }

  void testAssert() {
    String[] expected = {"bar", "baz", "foo"};
    List<String> actual = Stream.of("foo", "bar", "baz").sorted().collect(Collectors.toList()); // or some other complex method call which result is actually tested
    org.junit.Assert.assertEquals(Arrays.asList(expected), actual); // warning: "Arguments to 'assertEquals()' in wrong order"
  }

  void testReferencedConstant() {
    String name = "foobar";
    Memento m = new Memento(name);
    Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(m.getName(), name);
  }

  public static void assertOutputEquals(String exp, int root) throws IOException {
    StringWriter writer = new StringWriter();
    writer.write(root);
    String actual = writer.toString();
    Assert.assertEquals(exp, actual);
  }
}
class Memento {
  private String myName;

  Memento(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}