public class Test {
    int method() {
        try {
            System.out.println("Text");
            return 0;
        } finally {
            return newMethod();
        }
    }

    private int newMethod() {
        System.out.println("!!!");
        return 1;
    }
}