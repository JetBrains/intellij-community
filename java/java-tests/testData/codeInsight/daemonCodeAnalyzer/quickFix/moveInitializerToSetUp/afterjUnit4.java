// "Move initializer to setUp method" "true"
public class X {
  <caret>int i;

    @org.junit.Before
    public void setUp() throws Exception {
        i = 7;
    }

    @org.junit.Test
  public void test() {
  }
}
