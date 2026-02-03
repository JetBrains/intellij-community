// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    record R() {}

    void foo(Object obj) {
        switch (obj) {
            case R r:
                System<caret>.out.println(42);
                break;
            default:
                System.out.println(42);
        }
    }
}
