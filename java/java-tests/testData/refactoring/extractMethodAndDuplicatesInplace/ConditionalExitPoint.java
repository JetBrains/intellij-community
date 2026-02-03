public class Test {
    public int test(int x) {
        int i;
        for (i = 0; i <= x; i++) {
            <selection>if (i == 42) {
                break;
            } else if (i == 17) {
                break;
            }</selection>
        }
        return i;
    }
}