class MethodReference27 {

    interface SAM {
        void m(int i1, int i2);
    }

    static void m1(int i1, int i2) { }
    static void m1(Integer i1, int i2) { }
    static void m1(int i1, Integer i2) { }
    static void m1(Integer i1, Integer i2) {}
    static void m1(Integer... is) { }
    
    static void m2(int... is) { }
    static void m2(double... ds) {}

    public static void main(String[] args) {
        SAM s1 = MethodReference27::m1;
        s1.m(42,42);
        SAM s2 = MethodReference27 :: m2;
        s2.m(42,42);
    }
}
