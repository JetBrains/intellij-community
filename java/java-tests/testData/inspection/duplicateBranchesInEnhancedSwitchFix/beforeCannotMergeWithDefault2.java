// "Merge with the default 'switch' branch" "false"
class Test {
    record R() {}

    void foo(Object o) {
        switch (o) {
            case R() -> System.out.println(<caret>"hello");
            default -> System.out.println("hello");
        }
    }
}
