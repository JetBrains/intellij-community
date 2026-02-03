class Test {
    public void test(int x , int y) {
        while (x < 10) {
            x++;
            if (x <= y) {
                newMethod(x, y);
            } else {
                System.out.println();
            }
        }
    }

    private void newMethod(int x, int y) {
        if (x == y) return;
        System.out.println();
    }
}