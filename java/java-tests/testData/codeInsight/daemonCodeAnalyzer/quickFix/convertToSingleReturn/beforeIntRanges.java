// "Transform body to single exit-point form" "true-preview"
class Test {
    int <caret>test(String[] strings) {
        for (String string : strings) {
            if (!string.isEmpty()) {
                return string.length(); // positive number
            }
        }
        return strings.length;
    }
}