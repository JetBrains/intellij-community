// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f(String p) {
        String r = null;
        try {
            while (true) {
                String n = next();
                if (n != null) {
                    String t = n.toLowerCase();
                    if (t.equals(p)) {
                        r = n;
                        break;
                    }
                }
            }
        } finally {
            System.out.println();
        }
        re<caret>turn r;
    }

    String next() {
        return "";
    }
}