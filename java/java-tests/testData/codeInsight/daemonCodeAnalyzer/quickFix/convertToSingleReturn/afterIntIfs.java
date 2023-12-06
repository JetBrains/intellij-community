// "Transform body to single exit-point form" "true-preview"
class Test {
    int test(String s) {
        int result = 1;
        if(s == null) {
            if (Math.random() > 0.5) {
                result = 2;
            } else {
                result = 4;
            }
        } else if(s.isEmpty()) {
            result = 3;
        } else {
            System.out.println(s);
        }
        return result;
    }
}