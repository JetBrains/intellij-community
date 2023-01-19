// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    void foo(Object o) {
        switch (o) {
            case String s -> {}
            case null, default -> System.out.println("hello");
        }
    }
}
