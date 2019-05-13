class X {
    void foo() {
        boolean x = false;
        for (int i = 0; i < 100; i++) {<selection>
            x = true;
            if (i == 30) {
                break;
            }
        </selection>}
    }
}