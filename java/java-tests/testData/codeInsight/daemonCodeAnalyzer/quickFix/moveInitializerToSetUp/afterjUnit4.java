import org.junit.Before;

// "Move initializer to setUp method" "true-preview"
public class X {
  <caret>int i;

    @Before
    public void setUp() throws Exception {
        i = 7;
    }

    @org.junit.Test
  public void test() {
  }
}
