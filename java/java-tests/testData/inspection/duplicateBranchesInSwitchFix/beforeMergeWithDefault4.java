// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    void foo(Object o) {
        switch (o) {
            case null:
                System.out.printl<caret>n(42);
                break;
            default:
                System.out.println(42);
        }
    }
}
