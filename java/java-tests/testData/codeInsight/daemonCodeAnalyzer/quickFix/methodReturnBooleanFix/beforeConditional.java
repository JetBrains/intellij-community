// "Make 'foo' return 'boolean'" "true"
class Test {
    void bar() {
        System.out.println(fo<caret>o() ? "x" : "y");
    }

    int foo() {
        return 0;
    }
}