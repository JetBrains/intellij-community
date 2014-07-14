// "Copy 'a' to temp final variable" "true"
class Test {
    public void test() {
        int a = 1;
        a = 2;
        Runnable r = () -> {
            System.out.println(<caret>a);
        };
    }
}
