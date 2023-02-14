// "Copy 'i' to effectively final temp variable" "true-preview"
class Main {
    void foo(int i) {
        switch (i) {
            case 42 -> () -> System.out.println(i<caret>)
            default -> {}
        }
        i = 0;
    }
}
