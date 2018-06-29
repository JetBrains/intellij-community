class A {
    public static final String FOO = "foo";
}

class B {
    @SuppressWarnings(A.FOO)
    String myFoo;
}