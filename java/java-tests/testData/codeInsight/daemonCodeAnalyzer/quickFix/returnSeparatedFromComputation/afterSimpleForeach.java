// "Move 'return' closer to computation of the value of 'r'" "true-preview"
class T {
    String f(String[] a) {
        for (String s : a) {
            if (s != null && s.contains("@")) {
                return s + ":" + s.length();
            }
        }
        return "";
    }
}