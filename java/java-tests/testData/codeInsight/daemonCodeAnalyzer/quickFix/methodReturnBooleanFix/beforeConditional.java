// "Make 'foo()' return 'boolean'" "true-preview"
class Test {
    void bar() {
        System.out.println(fo<caret>o() ? "x" : "y");
    }

    int foo() {
        return 0;
    }
}