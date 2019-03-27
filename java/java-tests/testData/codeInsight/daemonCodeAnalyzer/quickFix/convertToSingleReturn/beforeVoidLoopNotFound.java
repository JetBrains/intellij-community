// "Transform body to single exit-point form" "true"
class Test {
    void <caret>test2(String[] arr) {
        for(String s : arr) {
            if (s.isEmpty()) {
                System.out.println(s);
                return;
            }
        }
        System.out.println("Not found");
    }
}