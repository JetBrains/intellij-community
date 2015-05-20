class Test {

    public static void main(String[] args) {
        final Test s = new Test();
        System.out.println(newMethod(s));
    }

    private static Object newMethod(Test s) {
        return s.callAbsentMethod().toString();
    }
}