// "Make 'foo()' return 'boolean'" "true-preview"
class Test {
    void bar() {
        System.out.println(foo() ? "x" : "y");
    }

    boolean foo() {
        return 0;
    }
}