import junit.framework.TestCase;
public class T extends TestCase {
    public void setUp() throws Exception {
        String na<caret>me = getName();
        myName = " second " + name;
    }

    public void test() throws Exception {
    }

    private String getName() {
        return null;
    }
}
