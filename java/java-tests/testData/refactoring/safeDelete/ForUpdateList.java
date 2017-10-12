class C {
    Object foo = null;

    void case02() {
        Object problematic;
        for(int i = 10; i > 0; i--, <caret>problematic = foo) {
            System.out.println("index = " + i);
        }
    }
}