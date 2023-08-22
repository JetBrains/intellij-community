// "Transform body to single exit-point form" "true-preview"
class Test {
    void test2(String[] arr) {
        boolean finished = false;
        for(String s : arr) {
            if (s.isEmpty()) {
                System.out.println(s);
                finished = true;
                break;
            }
        }
        if (!finished) {
            System.out.println("Not found");
        }
    }
}