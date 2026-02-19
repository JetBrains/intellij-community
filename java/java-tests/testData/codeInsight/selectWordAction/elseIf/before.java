
public class TestClass {

    public void test() {
        if (true) {
            System.out.println();
        }
        <caret>else if (true) {
            System.out.println();
        }
        else {

        }
    }
}