class Test {
    boolean x(Object obj) {
        return obj instanceof String && (<selection>(String) obj</selection>).isEmpty();
    }
}
