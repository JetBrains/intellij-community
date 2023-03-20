// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    void foo(Object o) {
        switch (o) {
            case null -> System.out.println(<caret>"hello");
            case String s -> {}
            default -> System.out.println("hello");
        }
    }
}
