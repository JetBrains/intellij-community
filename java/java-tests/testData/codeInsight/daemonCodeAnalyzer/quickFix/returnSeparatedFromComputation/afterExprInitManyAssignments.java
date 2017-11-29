// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    int foo(String s) {
        int r = s.length();
        if (s.isEmpty()) {
            return r;
        }

        String t = s.substring(1);
        if (!t.isEmpty()) {
            return t.length();
        }

        return r;
    }
}