class C {
    Object foo = null;

    void case01() {
        int i;
        for(i = 10; (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}