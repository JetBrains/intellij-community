// "Merge with the default 'switch' branch" "true"
class Test {
    record R() {}

    void foo(Object obj) {
        switch (obj) {
            case R() when true:
            default:
                System.out.println(42);
        }
    }
}
