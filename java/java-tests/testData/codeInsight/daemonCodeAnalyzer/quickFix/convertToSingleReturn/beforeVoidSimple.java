// "Transform body to single exit-point form" "true"
class Test {
    void <caret>test2(String s) {
        if(s == null) return;
        if(s.isEmpty()) return;
        System.out.println(s);
        return;
    }
}