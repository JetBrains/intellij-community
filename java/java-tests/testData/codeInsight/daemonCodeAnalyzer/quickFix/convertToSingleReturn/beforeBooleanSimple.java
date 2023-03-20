// "Transform body to single exit-point form" "true-preview"
class Test {
    boolean <caret>test(String s) {
        if(s == null) return false;
        if(s.isEmpty()) return false;
        System.out.println(s);
        return true;
    }
}