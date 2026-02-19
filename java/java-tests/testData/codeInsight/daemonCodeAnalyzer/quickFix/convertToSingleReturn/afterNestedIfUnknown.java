// "Transform body to single exit-point form" "true-preview"
class Test {
    String test(String[] strings) {
        String result = "";
        if (strings.length > 2) {
            String string = strings[0];
            if (string.equals(strings[1])) {
                result = foo(string);
            }
        }
        return result;
    }
}