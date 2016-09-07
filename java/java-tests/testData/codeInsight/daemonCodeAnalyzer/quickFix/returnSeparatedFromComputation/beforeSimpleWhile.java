// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f() {
        String r = "";
        while (hasNext()) {
            String s = next();
            if (s != null) {
                r = s;
                break;
            }
        }
        re<caret>turn r;
    }

    boolean hasNext() {
        return true;
    }

    String next() {
        return null;
    }
}