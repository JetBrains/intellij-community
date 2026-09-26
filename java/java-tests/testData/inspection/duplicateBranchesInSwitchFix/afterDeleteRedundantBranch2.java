// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    void foo(String str) {
        switch (str) {
            case null:
                System.out.println(42);
                break;
            default:
                System.out.println(42);
        }
    }
}
