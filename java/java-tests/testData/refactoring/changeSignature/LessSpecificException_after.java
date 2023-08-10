class Test {
    int foo() throws Exception { return 1;}

    void fooBar() throws IllegalArgumentException{
        int a = 0;
        try {
            a = foo();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(a);
    }
}