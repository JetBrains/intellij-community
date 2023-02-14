// "Transform body to single exit-point form" "true-preview"
class Test {
    void test(String s) {
        boolean finished = false;
        if (s != null) {
            int a = 0;
            synchronized (this) {
                if (s.isEmpty()) {
                    finished = true;
                } else {
                    a = s.length();
                }
            }
            if (!finished) {
                System.out.println(a);
            }
        }
    }
}