// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f(String[] a) {
        String r = "";
        for (String s : a) {
            if (s != null && s.contains("@")) {
                r = s + ":" + s.length();
                break;
            }
        }
        r<caret>eturn r;
    }
}