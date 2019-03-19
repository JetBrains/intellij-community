// "Transform body to single exit-point form" "true"
class Test {
    String test(String[] strings) {
        String result = null;
        boolean finished = false;
        if (strings.length > 2) {
            String string = strings[0];
            if (string.equals(strings[1])) {
                finished = true;
                result = foo(string);
            }
        }
        if (!finished) {
            result = bar();
        }
        return result;
    }
}