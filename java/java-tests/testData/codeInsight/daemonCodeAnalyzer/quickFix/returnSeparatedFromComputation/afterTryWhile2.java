// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    String f(String p) {
        try {
            while (true) {
                String n = next();
                if (n != null) return null;
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