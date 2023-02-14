// "Transform body to single exit-point form" "true-preview"
class Test {
    String <caret>test(int i) {
        if (i > 0) {
            if (i == 10) return null;
            System.out.println(i);
        }
        return String.valueOf(i);
    }
}