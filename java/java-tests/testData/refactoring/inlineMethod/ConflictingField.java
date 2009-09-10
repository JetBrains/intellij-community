class Test {
    public int i;

    public int <caret>getI() {
        return i;
    }

    public void usage() {
        int i = getI();
    }
}