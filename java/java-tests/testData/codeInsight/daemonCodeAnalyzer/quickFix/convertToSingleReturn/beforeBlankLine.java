// "Transform body to single exit-point form" "true-preview"
class Test {
    String <caret>test(String s) {
        try {
            Integer.parseInt(s);
        }
        catch (NumberFormatException ex) {
            return "Cannot parse number";
        }

        return "Ok";
    }
}