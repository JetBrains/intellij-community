// "Copy 'i' to effectively final temp variable" "true-preview"
class Main {
    void foo(Object obj) {
        int i = 42;
        int finalI = i;
        switch (obj) {
            case String s when switch ((Object) s.length()) {
                case Integer integer when integer == finalI -> 0;
                default -> 42;
            } == 42 -> {}
            default -> {}
        }
        i = 0;
    }
}