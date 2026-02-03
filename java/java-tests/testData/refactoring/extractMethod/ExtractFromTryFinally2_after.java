class Tester {
    String x() {
        String o = "";
        return newMethod(o);
    }

    private String newMethod(String o) {
        String s;
        try {
            s = o;
        }
        finally {
        }
        return s;
    }
}