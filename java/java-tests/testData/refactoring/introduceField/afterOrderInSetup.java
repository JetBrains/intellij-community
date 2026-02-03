import junit.framework.TestCase;
public class T extends TestCase {
    private String i;

    public void setUp() throws Exception {
        i = getName();
        myName = " second " + i;
    }

    public void test() throws Exception {
    }

    private String getName() {
        return null;
    }
}
