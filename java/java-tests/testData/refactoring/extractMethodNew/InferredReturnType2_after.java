class Test {
    public Object test(boolean b) {
        return newMethod(b);
    }

    private double newMethod(boolean b) {
        if (b) {
            return 42;
        }
        if (!b) {
            return 42.0;
        }
        return 42l;
    }
}