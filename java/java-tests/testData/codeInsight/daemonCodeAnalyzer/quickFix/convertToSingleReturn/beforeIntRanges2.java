// "Transform body to single exit-point form" "true"
class Test {
    int <caret>test(String[] strings) {
        for (String string : strings) {
            if (!string.equal("foo")) {
                return string.length(); // non-negative number
            }
        }
        return strings.length;
    }
}