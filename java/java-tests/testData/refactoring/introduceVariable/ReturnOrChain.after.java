class Test {
    boolean x(String s) {
        if (s == null) return true;
        String temp = s.trim();
        return temp.isEmpty();
    }
}
