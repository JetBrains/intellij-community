import junit.framework.TestCase;

class Base extends TestCase {
    public void setUp() {
        super.setUp();
    }
}

public class T extends Base {
    public void test() {
        int <caret>i = 9;
    }
}
