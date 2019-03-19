// "Transform body to single exit-point form" "true"
class Test {
    int test(String s) {
        int res = -1;
        boolean finished = false;
        try {
            res = Integer.parseInt(s);
            finished = true;
        } catch (NumberFormatException ex) {
            boolean result = s.isEmpty();
            if (result) {
                finished = true;
            }
        }
        if (!finished) {
            System.out.println("oops");
            res = -2;
        }
        return res;
    }
}