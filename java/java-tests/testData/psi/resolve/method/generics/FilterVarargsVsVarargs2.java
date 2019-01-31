class XY {
    private void f(byte... bs) {}

    private void f(int... is) {}

    public static void main(String[] args) {
            <caret>f((byte)0, (byte)1);
    }
}