class C {
    void foo(boolean a, boolean b, boolean c, boolean d, boolean e) {
        if (a &&/*c1*/ b &<caret>& c &&/*c2*/ d) {
            System.out.println("abcd");
        } else if(a /*c3*/ && b && e) {
            System.out.println("abe");
        } else if(a && /*c4*/ c) {
            System.out.println("ac");
        }
    }
}