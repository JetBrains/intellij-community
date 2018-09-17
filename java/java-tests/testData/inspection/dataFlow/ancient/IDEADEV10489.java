class IDEADEV10489 {
    static native String getS();

    static void f() {
        String s = getS();
        if (s != null) s.length();
        if (foo()) {
          int[] i1 = new int [s.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>()];
        } else if (foo()) {
          int[] i2 = new int [] {s.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>()};
        } else if (foo()) {
          int[][] i3 = new int [(s = "").length()][s.length()];
        } else{
          int[] i4 = new int[] {(s = "").length(), s.length()};
        }
    }

    private static native boolean foo();
}