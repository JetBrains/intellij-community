// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f(String p) {
        String r = null;
        try {
            while (true) {
                String n = next();
                if (n != null) return r;
                if ("@".eqals(n)) {
                    String t = n.toLowerCase();
                    if (t.equals(p)) {
                        return n;
                    }
                }
            }
        } finally {
            System.out.println();
        }
    }

    String next() {
        return "";
    }
}