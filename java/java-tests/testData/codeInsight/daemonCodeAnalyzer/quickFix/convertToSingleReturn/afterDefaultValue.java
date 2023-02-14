// "Transform body to single exit-point form" "true-preview"
class Test {
    String test(int i) {
        String result = null;
        boolean finished = false;
        if (i > 0) {
            if (i == 10) {
                finished = true;
            } else {
                System.out.println(i);
            }
        }
        if (!finished) {
            result = String.valueOf(i);
        }
        return result;
    }
}