// "Copy 'i' to effectively final temp variable" "true-preview"
class Main {
    void foo(int i) {
        int finalI = i;
        switch (i) {
            case 42 -> () -> System.out.println(finalI)
            default -> {}
        }
        i = 0;
    }
}
