class Test {
    void test1(){
        extracted("string", "string");
    }

    private static void extracted(String string, String string1) {
        System.out.println(string);
        System.out.println(string1);
    }

    void test2(){
        extracted("message", "message");
    }

    void test3(){
        extracted("first", "second");
    }
}