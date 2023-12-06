// "Remove unreachable branches" "true-preview"
class Test {
    final String s = "abc";

    int test() {
        return switch (s) {
            case <caret>String ss when ss.length() <= 3 -> 1;
            case "fsd" -> 2;
            default -> 3;
        };
    }
}