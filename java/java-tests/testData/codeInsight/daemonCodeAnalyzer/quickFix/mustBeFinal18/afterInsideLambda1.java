// "Copy 'a' to effectively final temp variable" "true-preview"
class Test {
    public void test() {
        int a = 1;
        a = 2;
        int finalA = a;
        Runnable r = () -> {
            System.out.println(finalA);
        };
    }
}
