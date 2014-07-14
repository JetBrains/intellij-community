import java.util.*;
class DiamondTest {

    public <U> void foo(ArrayList<U> p) {}

    public void test() {
        DiamondTest diamondTest = new DiamondTest();
        diamondTest.<Integer>foo(new ArrayList<>(3));
    }
}