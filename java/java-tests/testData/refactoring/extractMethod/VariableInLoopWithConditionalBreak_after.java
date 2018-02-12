class X {
    void foo() {
        boolean x = false;
        for (int i = 0; i < 100; i++) {
            if (newMethod(i)) break;
        }
    }

    private boolean newMethod(int i) {
        boolean x;
        x = true;
        if (i == 30) {
            return true;
        }
        return false;
    }
}