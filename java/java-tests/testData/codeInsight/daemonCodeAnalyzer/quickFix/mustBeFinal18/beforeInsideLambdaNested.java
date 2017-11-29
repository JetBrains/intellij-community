// "Copy 'a' to effectively final temp variable" "true"
class Test {
    public void test() {
        int a = 1;
        a = 2;
        Runnable r = () -> {
            Runnable rr = () -> System.out.println(<caret>a);
        };
    }
}
