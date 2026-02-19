public class Test {

    void test() {
        <selection>if (isGood() && isApplicable()) {
            System.out.println();
        }</selection>
        if (!isGood() && isApplicable()) {
            System.out.println();
        }
    }

    boolean isGood() { return true; }

    boolean isApplicable() { return true; }
}