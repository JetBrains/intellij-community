// "Transform body to single exit-point form" "true"
class Test {
    int test(String s) {
        int result = 2;
        if (s == null) {
            if (!(Math.random() > 0.5)) {
                result = 4;
            }
        } else {
            if (s.isEmpty()) {
                result = 3;
            } else {
                System.out.println(s);
                result = 1;
            }
        }
        return result;
    }
}