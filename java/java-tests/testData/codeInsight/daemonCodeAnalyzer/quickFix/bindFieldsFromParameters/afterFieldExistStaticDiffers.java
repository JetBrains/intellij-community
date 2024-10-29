// "Bind method parameters to fields" "true-preview"

class Bar {
    private static int f4;
    private int f1;
    private static boolean f2;
    private static final int f3 = 123;
    
    static void test(int f1, int f2, int f3, int f4) {
        Bar.f4 = f4;
    }
}