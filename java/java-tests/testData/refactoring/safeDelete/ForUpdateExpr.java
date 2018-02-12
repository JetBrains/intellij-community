class C {
    Object foo = null;

    void case01() {
        Object problematic;
        for(int i = 10; (--i) > 0; <caret>problematic = foo) {
            System.out.println("index = " + i);
        }
    }
}