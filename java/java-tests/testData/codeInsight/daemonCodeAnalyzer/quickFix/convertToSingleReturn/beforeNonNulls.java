// "Transform body to single exit-point form" "true-preview"
class Test {
    String<caret> process(String s, int x) {
        if (x > 0) {
            if (x == 2) {
                return s.trim();
            }
            System.out.println(s.substring(0));
        }
        return s.substring(1);
    }
}