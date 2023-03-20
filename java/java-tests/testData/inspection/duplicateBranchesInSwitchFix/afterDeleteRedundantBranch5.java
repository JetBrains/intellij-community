// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    void foo(String o) {
        switch (o) {
            default:
                System.out.println(42);
                break;
        }
    }
}
