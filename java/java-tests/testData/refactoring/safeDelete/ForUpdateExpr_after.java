class C {
    Object foo = null;

    void case01() {
        for(int i = 10; (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}