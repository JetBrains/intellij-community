// "Transform body to single exit-point form" "true-preview"
class Test {
    String test(String s) {
        String result = null;
        try {
            Integer.parseInt(s);
        }
        catch (NumberFormatException ex) {
            result = "Cannot parse number";
        }
        if (result == null) {
            result = "Ok";
        }

        return result;
    }
}