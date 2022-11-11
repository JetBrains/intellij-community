// "Transform body to single exit-point form" "true-preview"
class Test {
    String process(String s, int x) {
        String result;
        if (x > 0) {
            if (x == 2) {
                result = s.trim();
            } else {
                result = s.substring(0);
            }
        } else {
            result = s.substring(1);
        }
        return result;
    }
}