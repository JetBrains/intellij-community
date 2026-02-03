class Test {
    boolean test(String s1, String s2) {
        if (s1 == null) return true;
        if (s2 == null) return false;
        String temp = s2.trim();
        return s1.equals(temp);
    }
}
