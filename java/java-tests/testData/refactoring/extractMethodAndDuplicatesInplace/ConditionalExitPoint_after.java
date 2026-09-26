public class Test {
    public int test(int x) {
        int i;
        for (i = 0; i <= x; i++) {
            if (extracted(i)) break;
        }
        return i;
    }

    private static boolean extracted(int i) {
        if (i == 42) {
            return true;
        } else if (i == 17) {
            return true;
        }
        return false;
    }
}