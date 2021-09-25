public class QualifiedTest extends junit.framework.TestCase {
  public void testFoo() {
    QualifiedTest.<warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>();
  }

  public static void assertEquals() {}
}