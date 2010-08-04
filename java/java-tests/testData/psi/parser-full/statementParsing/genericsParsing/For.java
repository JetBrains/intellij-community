class Test {
    void method() {
        for(Iterator<String> it = null; it.hasNext();) {
            String s = it.next();
        }
    }
}