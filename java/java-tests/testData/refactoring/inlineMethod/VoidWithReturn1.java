class Test {
    private static void dude() {
        return System.currentTimeMillis();
    }

    public static void main(String[] args) {
        <caret>dude();
    }
}