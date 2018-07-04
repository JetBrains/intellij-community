class C {
    void foo(boolean a, boolean b, boolean c, boolean d, boolean e) {
        /*c3*/
        /*c4*/
        if (a &&/*c1*/ b) {
            if (c &&/*c2*/ d) {
                System.out.println("abcd");
            } else if (e) {
                System.out.println("abe");
            } else {
                System.out.println("ab");
            }
        }
    }
}