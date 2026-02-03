// "Transform body to single exit-point form" "true-preview"
class Test {
    int <caret>test(String s) {
        try {
            return Math.abs(Integer.parseInt(s));
        }
        catch(NumberFormatException ex) {
            boolean result = s.isEmpty();
            if (result) return -1;
        }
        System.out.println("oops");
        return -2;
    }
}