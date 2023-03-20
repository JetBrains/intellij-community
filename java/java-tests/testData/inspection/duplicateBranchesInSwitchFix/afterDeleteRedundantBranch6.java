// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    record R() {}

    void foo(Object obj) {
        switch (obj) {
            default:
                System.out.println(42);
        }
    }
}
