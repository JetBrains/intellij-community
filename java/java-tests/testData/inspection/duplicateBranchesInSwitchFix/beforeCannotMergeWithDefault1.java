// "Merge with the default 'switch' branch" "false"
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
