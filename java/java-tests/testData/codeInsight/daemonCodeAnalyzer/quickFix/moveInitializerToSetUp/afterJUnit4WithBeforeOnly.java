// "Move initializer to setUp method" "true-preview"
public class X {
  int i;

    @org.junit.Before
    public void setUp() throws Exception {

        i = 7;
    }
}
