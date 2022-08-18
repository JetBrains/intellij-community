
public class TestClass {

    public void test() {
        if (true) {
            System.out.println();
        }
<caret><selection>        else if (true) {
            System.out.println();
        }
</selection>        else {

        }
    }
}