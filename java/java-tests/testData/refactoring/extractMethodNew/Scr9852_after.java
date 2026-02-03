class Test {
    int f() {
        try {
            return newMethod();
        } finally {
        }
    }

    private int newMethod() {
        int k = 0;
        return k;
    }
}