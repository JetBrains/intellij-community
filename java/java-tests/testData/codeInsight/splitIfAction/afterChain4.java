class C {
    void foo(boolean a, boolean b, boolean c, boolean d, boolean e, boolean f) {
        if (a && b) {
            if (c) {
                System.out.println("1");
            } else if (d && e && f) {
                System.out.println("2");
            }
        } else if (d && e && f) {
            System.out.println("2");
        }
    }
}