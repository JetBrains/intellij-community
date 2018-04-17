class C {
    void foo(boolean a, boolean b, boolean c, boolean d, boolean e) {
        /*c3*/
        if (a &&/*c1*/ b) {
            if (c &&/*c2*/ d) {
                System.out.println("abcd");
            } else if (e) {
                System.out.println("abe");
            } else if (a && /*c4*/ c) {
                System.out.println("ac");
            }
        } else if (a && /*c4*/ c) {
            System.out.println("ac");
        }
    }
}