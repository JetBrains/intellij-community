// "Move initializer to setUp method" "true"
package junit.framework;

public class X extends TestCase {
  int i;

    public void setUp() throws Exception {
        super.setUp();
        i = 7;
    }
}

//HACK: making test possible without attaching jUnit
public abstract class TestCase {
}
