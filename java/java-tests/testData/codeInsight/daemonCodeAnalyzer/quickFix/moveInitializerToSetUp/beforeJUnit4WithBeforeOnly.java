// "Move initializer to setUp method" "true"
public class X {
  <caret>int i = 7;

    @org.junit.Before
    public void setUp() throws Exception {

    }
}
