class Test {
    public void test(int x, int y) {
        while (x < y) {
            if (newMethod(x, y)) continue;
            x++;
        }
    }

    private boolean newMethod(int x, int y) {
        if (x == y) return true;
        return false;
    }
}