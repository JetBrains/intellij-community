public class Test {

    void test() {
        extracted(isGood());
        extracted(!isGood());
    }

    private void extracted(boolean Good) {
        if (Good && isApplicable()) {
            System.out.println();
        }
    }

    boolean isGood() { return true; }

    boolean isApplicable() { return true; }
}