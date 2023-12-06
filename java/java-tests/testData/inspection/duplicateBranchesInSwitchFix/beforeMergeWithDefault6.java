// "Merge with the default 'switch' branch" "true"
class Test {
    record R() {}

    void foo(Object obj) {
        switch (obj) {
            case R() when true:
                System<caret>.out.println(42);
                break;
            default:
                System.out.println(42);
        }
    }
}
