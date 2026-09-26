class Test {
    String x(String s) {
        String x;
        if (s == null) {
            x = "";
        } else {
            String temp = s.trim();
            x = temp;
        }
        return x;
    }
}
