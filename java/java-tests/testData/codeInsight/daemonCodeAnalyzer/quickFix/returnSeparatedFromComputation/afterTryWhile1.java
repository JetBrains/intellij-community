// "Move 'return' closer to computation of the value of 'r'" "true-preview"
class T {
    String f(String p) {
        try {
            while (true) {
                String n = next();
                if (n != null) {
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