class Test {
    int <caret>foo() throws IllegalArgumentException { return 1;}

    void fooBar() throws IllegalArgumentException{
        int a = foo();
        System.out.println(a);
    }
}