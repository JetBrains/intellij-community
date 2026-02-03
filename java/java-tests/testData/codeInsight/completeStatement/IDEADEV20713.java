
class T {
    void test(Object a) {
        if (true) <caret>
        if (a != null)
            System.out.println("a = " + a.toString());
    }
}