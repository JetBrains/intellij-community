public /*abstract*/ class WillWorkTest {
    int opera() {
        int i = 0;

        return newMethod(i);
    }

    private int newMethod(int i) {
        int k;
        if (true) k = i;
        return k;
    }
}
