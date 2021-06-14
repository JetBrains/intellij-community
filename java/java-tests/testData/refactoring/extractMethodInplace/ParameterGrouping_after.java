class Test {
    void test1(){
        extracted("string", "string");
    }

    private void extracted(String string, String string2) {
        System.out.println(string);
        System.out.println(string2);
    }

    void test2(){
        extracted("message", "message");
    }

    void test3(){
        extracted("first", "second");
    }
}