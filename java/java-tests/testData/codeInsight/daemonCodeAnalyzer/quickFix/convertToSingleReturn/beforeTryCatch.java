// "Transform body to single exit-point form" "true-preview"
class Test {
    int <caret>test(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch(NumberFormatException ex) {
            return -1;
        }
    }
}