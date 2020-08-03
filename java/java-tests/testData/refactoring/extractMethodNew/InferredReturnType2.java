class Test {
    public Object test(boolean b) {
        <selection>if (b) {
            return 42;
        }
        if (!b) {
            return 42.0;
        }
        return 42l;</selection>
    }
}