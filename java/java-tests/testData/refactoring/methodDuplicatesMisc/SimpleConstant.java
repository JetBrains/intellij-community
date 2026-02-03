class Test {
    public static final String A<caret>BC = "abc";
    void foo() {
        System.out.println("abc");
    }
}

class Foo {
    void bar() {
        System.out.println("abc");
    }
}