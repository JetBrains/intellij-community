// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    record R() {}
    record S() {}

    void foo(Object obj) {
        switch (obj) {
            case R():
            case null:
            case S():
                <caret>return 42;
            case String s:
                return 0;
            default:
                return 42;
      }
    }
}
