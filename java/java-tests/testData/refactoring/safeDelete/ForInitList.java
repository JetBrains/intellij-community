class C {
    Object foo = null;

    void case01() {
        Object problematic;
        int i;
        for(i = 10, <caret>problematic = foo; (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}