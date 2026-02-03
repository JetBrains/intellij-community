// "Transform body to single exit-point form" "true-preview"
class Test {
    int test(String s) {
        int res = -2;
        try {
            res = Math.abs(Integer.parseInt(s));
        }
        catch(NumberFormatException ex) {
            boolean result = s.isEmpty();
            if (result) {
                res = -1;
            }
        }
        if (res == -2) {
            System.out.println("oops");
        }
        return res;
    }
}