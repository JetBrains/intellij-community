// "Transform body to single exit-point form" "true-preview"
class Test {
    void test2(String s) {
        if(s != null) {
            if(!s.isEmpty()) {
                System.out.println(s);
            }
        }
    }
}