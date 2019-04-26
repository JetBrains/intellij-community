// "Transform body to single exit-point form" "true"
class Test {
    void test2(String[] arr) {
        for (String s : arr) {
            if (s.isEmpty()) {
                System.out.println(s);
                break;
            }
        }
    }
}