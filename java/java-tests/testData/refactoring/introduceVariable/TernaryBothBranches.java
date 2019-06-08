class A {
    public Integer incrementInteger(Number n) {
        return n instanceof Integer ? <selection>(Integer)n + 1</selection> : (Integer) n + 1 -1 ;
    }
}