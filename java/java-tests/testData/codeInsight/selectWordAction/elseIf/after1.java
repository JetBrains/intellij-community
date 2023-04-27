
public class TestClass {

    public void test() {
        if (true) {
            System.out.println();
        }
        <selection><caret>else</selection> if (true) {
            System.out.println();
        }
        else {

        }
    }
}