public interface FromStaticMethodInInterface {
    static void test(String a, String b) {

        newMethod(a, b);

    }

    static void newMethod(String a, String b) {
        String c = a + b;
        System.out.println(c);
    }
}