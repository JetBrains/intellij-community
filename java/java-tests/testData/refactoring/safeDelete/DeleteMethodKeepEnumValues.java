enum Test {
    A, B;
    void f<caret>oo() {
        for (Test v : values()) {
            System.out.println(v);
        }
    }

}