// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f() {
        String r = "";
        while (hasNext()) {
            String s = next();
            if (s != null) {
                return s;
            }
        }
        return r;
    }

    boolean hasNext() {
        return true;
    }

    String next() {
        return null;
    }
}