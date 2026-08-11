// "Bind constructor parameters to fields" "true-preview"

class A {
    public A(String s) {
    }
}

class B extends A {
    private final String myName;

    public B(String s, String name) {
        super(s);
        myName = name;
    }
}
