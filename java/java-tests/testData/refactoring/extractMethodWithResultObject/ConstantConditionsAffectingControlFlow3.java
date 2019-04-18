class WillWorkTest {
    int opera(boolean b) {
        int i = 0;
        int k;
        if (b) k = 2;
        <selection>
        if (true) k = i;
        return k;</selection>
    }
}
