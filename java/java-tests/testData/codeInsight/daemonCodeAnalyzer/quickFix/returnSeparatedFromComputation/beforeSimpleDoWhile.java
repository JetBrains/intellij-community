// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f(String a) {
        String r = "";
        int i = 0;
        do {
            int j = a.indexOf(",", i);
            String s = j > i ? a.substring(i, j) : a.substring(i);
            if (s.startsWith("@")) {
                r = s;
                break;
            }
            i = j + 1;
        }
        while (i >= 0);
        retu<caret>rn r;
    }

    boolean hasNext() {
        return true;
    }

    String next() {
        return null;
    }
}
