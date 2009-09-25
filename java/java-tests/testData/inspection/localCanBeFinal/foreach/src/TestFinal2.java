public class TestFinal2 {
    static void foo(final boolean[] b) {
        for(boolean bx : b) {
            System.out.println("bx = " + bx);
        }
        for(boolean by : b) {
            by = false;
        }
    }
}
