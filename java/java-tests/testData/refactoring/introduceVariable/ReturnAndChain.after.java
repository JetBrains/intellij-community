class Test {
    boolean x(Object obj) {
        if (!(obj instanceof String)) return false;
        String temp = (String) obj;
        return temp.isEmpty();
    }
}
