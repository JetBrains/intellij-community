// "Transform 'i' into final one element array" "true-preview"
class Main {
    void foo(Object obj) {
        final int[] i = {42};
        switch (obj) {
            case String s when switch ((Object) s.length()) {
                case Integer integer when integer == ++i[0] -> 0;
                default -> 42;
            } == 42 -> {}
            default -> {}
        }
    }
}