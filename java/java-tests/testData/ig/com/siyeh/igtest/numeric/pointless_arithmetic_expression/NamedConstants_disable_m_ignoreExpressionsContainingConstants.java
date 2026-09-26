class PointlessArithmetic {
    static final int ZERO = 0;
    static final int ZERO_TOO = 0;
    static final int ONE = 1;
    static final int ONE_TOO = 1;
    static final int TWO = 2;
    static final int TWO_TOO = 2;
    static final int MINUS_TWO = -2;

    void smokes(int a) {
        int i1 = <warning descr="'ZERO * a' can be replaced with '0'">ZERO * a</warning>;
        int i2 = <warning descr="'ONE * a' can be replaced with 'a'">ONE * a</warning>;
        int i3 = TWO * a; // not considered pointless, even though we can infer it statically

        int j1 = TWO - 2;
        int j2 = <warning descr="'TWO - TWO' can be replaced with '0'">TWO - TWO</warning>;
        int j3 = TWO - TWO_TOO;
        int j4 = <warning descr="'TWO - TWO_TOO - 0' can be replaced with 'TWO - TWO_TOO'">TWO - TWO_TOO - 0</warning>;
        int j5 = <warning descr="'TWO - TWO_TOO + a' can be replaced with 'a'">TWO - TWO_TOO + a</warning>; // Case from IDEA-364437
        int j6 = <warning descr="'TWO - 2 + a' can be replaced with 'a'">TWO - 2 + a</warning>;
    }

    public void add(int a) {
      int i = <warning descr="'a + ZERO' can be replaced with 'a'">a + ZERO</warning>;
      int i1 = <warning descr="'a + ZERO + ZERO_TOO' can be replaced with 'a'">a + ZERO + ZERO_TOO</warning>;
      int j = <warning descr="'a + ZERO + ZERO' can be replaced with 'a'">a + ZERO + ZERO</warning>;
      int k = <warning descr="'a + (ZERO)' can be replaced with 'a'">a + (ZERO)</warning>;
      int l = <warning descr="'((a)) + (ZERO)' can be replaced with '((a))'">((a)) + (ZERO)</warning>;
      int m = a + ONE;
      int n = a + ONE + TWO;
      int o = <warning descr="'ZERO + ZERO' can be replaced with '0'">ZERO + ZERO</warning>;
      int p = <warning descr="'((ZERO)) + ZERO' can be replaced with '0'">((ZERO)) + ZERO</warning>;
      int r = ONE + ONE;

      final int localZero = 0;
      int s = <warning descr="'localZero + localZero' can be replaced with '0'">localZero + localZero</warning>;
    }

    public void subtract(int someVar) {
      int a1 = <warning descr="'someVar - ZERO' can be replaced with 'someVar'">someVar - ZERO</warning>;
      int a2 = <warning descr="'someVar - (ZERO)' can be replaced with 'someVar'">someVar - (ZERO)</warning>;
      int a3 = <warning descr="'((someVar)) - (ZERO)' can be replaced with '((someVar))'">((someVar)) - (ZERO)</warning>;
      int a4 = <warning descr="'TWO - 2 + someVar' can be replaced with 'someVar'">TWO - 2 + someVar</warning>;
      int a5 = <warning descr="'MINUS_TWO + 2 - someVar' can be replaced with '- someVar'">MINUS_TWO + 2 - someVar</warning>;
      int a6 = <warning descr="'MINUS_TWO + ZERO' can be replaced with 'MINUS_TWO'">MINUS_TWO + ZERO</warning> - someVar;
      int a7 = someVar - ONE;
      int a8 = someVar - ONE - TWO;
      int a9 = someVar - ONE - TWO + someVar;
      int a10 = <warning descr="'ZERO - ZERO' can be replaced with 'ZERO'">ZERO - ZERO</warning>;
      int a11 = <warning descr="'((ZERO)) - ZERO' can be replaced with '((ZERO))'">((ZERO)) - ZERO</warning>;
  
      final int localZero = 0;
      int g = <warning descr="'localZero - localZero' can be replaced with 'localZero'">localZero - localZero</warning>;
    }

    public void multiply(int someVar) {
      int a1 = <warning descr="'someVar * ONE' can be replaced with 'someVar'">someVar * ONE</warning>;
      int a2 = someVar * (<warning descr="'ONE * someVar' can be replaced with 'someVar'">ONE * someVar</warning>);
      int a3 = <warning descr="'someVar * ONE * ONE' can be replaced with 'someVar'">someVar * ONE * ONE</warning>;
      int a4 = <warning descr="'someVar * ONE * ONE * someVar' can be replaced with 'someVar * someVar'">someVar * ONE * ONE * someVar</warning>;
      int a5 = <warning descr="'((someVar)) * (ONE)' can be replaced with '((someVar))'">((someVar)) * (ONE)</warning>;
      int a6 = someVar * someVar;
      int a7 = someVar * someVar * someVar;
      int a8 = someVar * someVar * someVar * TWO;
  
      final int localZero = 0;
      final int localOne = 1;
      int x = <warning descr="'someVar * localZero' can be replaced with '0'">someVar * localZero</warning>;
      int y = <warning descr="'someVar * localOne' can be replaced with 'someVar'">someVar * localOne</warning>;
    }

    public void divide(int a) {
      int i = <warning descr="'ONE / ONE' can be replaced with 'ONE'">ONE / ONE</warning>;
      int j = <warning descr="'ONE / ONE_TOO' can be replaced with 'ONE'">ONE / ONE_TOO</warning>;
      int k = <warning descr="'TWO / ONE' can be replaced with 'TWO'">TWO / ONE</warning>;
      int l = <warning descr="'a / ONE' can be replaced with 'a'">a / ONE</warning>;
      int m = a / TWO; // not considered pointless, even though we can infer it statically
      int n = <warning descr="'a / 1' can be replaced with 'a'">a / 1</warning>; // not a named constant
    }
}
