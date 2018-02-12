// "Move 'return' closer to computation of the value of 'r'" "true"
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