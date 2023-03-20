// "Transform body to single exit-point form" "true-preview"
class Test {
    String <caret>test(String[] strings) {
        if (strings.length > 2) {
            String string = strings[0];
            if (string.equals(strings[1])) {
                return foo(string);
            }
        }
        return bar();
    }
}