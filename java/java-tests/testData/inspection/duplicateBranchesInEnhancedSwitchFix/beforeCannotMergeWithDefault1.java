// "Merge with the default 'switch' branch" "false"
class Test {
    void foo(Object o) {
        switch (o) {
            case String s -> System.out.println(<caret>"hello");
            default -> System.out.println("hello");
        }
    }
}
