public class Test {
    int method() {
        try {
            return newMethod("Text", 0);
        } finally {
            return newMethod("!!!", 1);
        }
    }

    private int newMethod(String s, int i) {
        System.out.println(s);
        return i;
    }
}