import java.util.Collections;
import java.util.Iterator;

public class AmbiguousTest extends AbstractTest {
  public void testFoo() {
    Iterator<Integer> list = Collections.singleton(1).iterator();
    assertEquals<error descr="Ambiguous method call: both 'Assert.assertEquals(Object, Object)' and 'Assert.assertEquals(long, long)' match">(1, list.next())</error>;
  }
}

abstract class AbstractTest extends Assert {
  public static void assertEquals(float expected, float actual) {
    Assert.assertEquals(expected, actual, 0.00001);
  }
}
class Assert  {
    protected Assert() { /* compiled code */ }
    public static void assertEquals(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }
    public static void assertEquals(java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }
    public static void assertEquals(java.lang.String message, double expected, double actual, double delta) { /* compiled code */ }
    public static void assertEquals(long expected, long actual) { /* compiled code */ }
    public static void assertEquals(java.lang.String message, long expected, long actual) { /* compiled code */ }
    /**
     * @deprecated
     */
    @java.lang.Deprecated
    public static void assertEquals(double expected, double actual) { /* compiled code */ }
    
    /**
     * @deprecated
     */
    @java.lang.Deprecated
    public static void assertEquals(java.lang.String message, double expected, double actual) { /* compiled code */ }
    
    public static void assertEquals(double expected, double actual, double delta) { /* compiled code */ }
    /**
     * @deprecated
     */
    @java.lang.Deprecated
    public static void assertEquals(java.lang.String message, java.lang.Object[] expecteds, java.lang.Object[] actuals) { /* compiled code */ }
    
    /**
     * @deprecated
     */
    @java.lang.Deprecated
    public static void assertEquals(java.lang.Object[] expecteds, java.lang.Object[] actuals) { /* compiled code */ }
}