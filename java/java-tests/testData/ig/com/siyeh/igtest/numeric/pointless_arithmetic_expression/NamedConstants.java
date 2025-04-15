class PointlessArithmetic {
    static final int ZERO = 0;
    static final int ZERO_TOO = 0;
    static final int ONE = 1;
    static final int ONE_TOO = 1;
    static final int TWO = 2;
    static final int TWO_TOO = 2;
    static final int MINUS_TWO = -2;

    void smokes(int a) {
        int i1 = ZERO * a;
        int i2 = ONE * a;
        int i3 = TWO * a;

        int j1 = TWO - 2;
        int j2 = <warning descr="'TWO - TWO' can be replaced with '0'">TWO - TWO</warning>;
        int j3 = TWO - TWO_TOO;
        int j4 = <warning descr="'TWO - TWO_TOO - 0' can be replaced with 'TWO - TWO_TOO'">TWO - TWO_TOO - 0</warning>;
        int j5 = TWO - TWO_TOO + a; // Case from IDEA-364437
        int j6 = TWO - 2 + a;
    }

    public void add(int a) {
      int i = a + ZERO;
      int i1 = a + ZERO + ZERO_TOO;
      int j = a + ZERO + ZERO;
      int k = a + (ZERO);
      int l = ((a)) + (ZERO);
      int m = a + ONE;
      int n = a + ONE + TWO;
      int o = ZERO + ZERO;
      int p = ((ZERO)) + ZERO;
      int r = ONE + ONE;

      final int localZero = 0;
      int s = localZero + localZero;
    }

    public void subtract(int someVar) {
      int a1 = someVar - ZERO;
      int a2 = someVar - (ZERO);
      int a3 = ((someVar)) - (ZERO);
      int a4 = TWO - 2 + someVar;
      int a5 = MINUS_TWO + 2 - someVar;
      int a6 = MINUS_TWO + ZERO - someVar;
      int a7 = someVar - ONE;
      int a8 = someVar - ONE - TWO;
      int a9 = someVar - ONE - TWO + someVar;
      int a10 = <warning descr="'ZERO - ZERO' can be replaced with '0'">ZERO - ZERO</warning>;
      int a11 = <warning descr="'((ZERO)) - ZERO' can be replaced with '0'">((ZERO)) - ZERO</warning>;
  
      final int localZero = 0;
      int g = <warning descr="'localZero - localZero' can be replaced with '0'">localZero - localZero</warning>;
    }

    public void multiply(int someVar) {
      int a1 = someVar * ONE;
      int a2 = someVar * (ONE * someVar);
      int a3 = someVar * ONE * ONE;
      int a4 = someVar * ONE * ONE * someVar;
      int a5 = ((someVar)) * (ONE);
      int a6 = someVar * someVar;
      int a7 = someVar * someVar * someVar;
      int a8 = someVar * someVar * someVar * TWO;
  
      final int localZero = 0;
      final int localOne = 1;
      int x = someVar * localZero;
      int y = someVar * localOne;
    }

    public void divide(int a) {
      int i = <warning descr="'ONE / ONE' can be replaced with '1'">ONE / ONE</warning>;
      int j = ONE / ONE_TOO;
      int k = TWO / ONE;
      int l = a / ONE;
      int m = a / TWO; // not considered pointless, even though we can infer it statically
      int n = <warning descr="'a / 1' can be replaced with 'a'">a / 1</warning>; // not a named constant
    }
}
