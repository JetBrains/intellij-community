// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    record R() {}
    record S() {}

    void foo(Object obj) {
        switch (obj) {
            case R():
            case S():
            case null, default:
                System.out.println(42);
        }
    }
}
