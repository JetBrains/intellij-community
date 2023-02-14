// "Transform body to single exit-point form" "true-preview"
class Test {
    String process(String s, int x) {
        String result = null;
        if (x > 0) {
            if (x == 2) {
                result = s.trim();
            } else {
                System.out.println(s.substring(0));
            }
        }
        if (result == null) {
            result = s.substring(1);
        }
        return result;
    }
}