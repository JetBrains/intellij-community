class C {
    Object foo = null;

    void case01() {
        Object problematic;
        int i = 10;
        for(<caret>problematic = foo; (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}