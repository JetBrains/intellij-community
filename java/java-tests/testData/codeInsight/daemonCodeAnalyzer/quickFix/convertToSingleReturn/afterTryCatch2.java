// "Transform body to single exit-point form" "true-preview"
class Test {
    int test(String s) {
        int res = -2;
        boolean finished = false;
        try {
            res = Integer.parseInt(s);
            finished = true;
        }
        catch(NumberFormatException ex) {
            boolean result = s.isEmpty();
            if (result) {
                res = -1;
                finished = true;
            }
        }
        if (!finished) {
            System.out.println("oops");
        }
        return res;
    }
}