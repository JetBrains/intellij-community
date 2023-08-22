// "Transform body to single exit-point form" "true-preview"
class Test {
    int test(String s) {
        int result;
        try {
            result = Integer.parseInt(s);
        }
        catch(NumberFormatException ex) {
            result = -1;
        }
        return result;
    }
}