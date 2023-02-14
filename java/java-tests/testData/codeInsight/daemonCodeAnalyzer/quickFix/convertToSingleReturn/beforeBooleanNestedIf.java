// "Transform body to single exit-point form" "true-preview"
class Test {
    boolean <caret>test(String[] arr) {
        if (arr == null) return false;
        String s = arr[0];
        if (s == null) return false;
        s = arr[1];
        if (s == null) return false;
        if (arr.length > 3) {
            s = arr[2];
            if (s != null && s.isEmpty()) return false;
        }
        return true;
    }
}