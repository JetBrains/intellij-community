// "Transform body to single exit-point form" "true"
class Test {
    int test(String s) {
        int result = -1;
        try {
            result = Integer.parseInt(s);
        } catch (NumberFormatException ex) {
        }
        return result;
    }
}