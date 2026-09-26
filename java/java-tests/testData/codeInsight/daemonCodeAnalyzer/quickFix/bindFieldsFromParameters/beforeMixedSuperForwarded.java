// "Bind constructor parameters to fields" "true-preview"

class A {
    public A(String s) {
    }
}

class B extends A {
    public <caret>B(String s, String name) {
        super(s);
    }
}
