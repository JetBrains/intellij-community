
public class TestClass {

    public void test() {
        if (true) {
            System.out.println();
        }
<selection>        <caret>else if (true) {
            System.out.println();
        }
</selection>        else {

        }
    }
}