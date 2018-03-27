// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f() {
        while (hasNext()) {
            String s = next();
            if (s != null) {
                return s;
            }
        }
        return "";
    }

    boolean hasNext() {
        return true;
    }

    String next() {
        return null;
    }
}