public interface FromPrivateMethodInInterface {
    private void test(String a, String b) {

        newMethod(a, b);

    }

    private void newMethod(String a, String b) {
        String c = a + b;
        System.out.println(c);
    }
}