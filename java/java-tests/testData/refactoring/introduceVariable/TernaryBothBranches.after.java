class A {
    public Integer incrementInteger(Number n) {
        int temp = (Integer) n + 1;
        return n instanceof Integer ? temp : temp -1 ;
    }
}