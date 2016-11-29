// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f() {
        String r = "";
        do {
            if (!hasNext()) return r;
            String s = next();
            if (s != null) {
                return s;
            }
        } while (true);
    }

    boolean hasNext() {
        return true;
    }

    String next() {
        return null;
    }
}
