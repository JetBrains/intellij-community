public interface FromStaticMethodInInterface {
    static void test(String a, String b) {
        <selection>
        String c = a + b;
        System.out.println(c);
        </selection>
    }
}