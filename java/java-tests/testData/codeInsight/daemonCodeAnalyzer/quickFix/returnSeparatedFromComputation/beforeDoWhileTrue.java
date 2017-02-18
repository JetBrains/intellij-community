// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f() {
        String r = "";
        do {
            if (!hasNext()) break;
            String s = next();
            if (s != null) {
                r = s;
                break;
            }
        } while (true);
        <caret>return r;
    }

    boolean hasNext() {
        return true;
    }

    String next() {
        return null;
    }
}
