class C {
    void foo(boolean a, boolean b, boolean c) {
        if (a) {
            if (b) {
                System.out.println("ab");
            } else if (c) {
                System.out.println("ac");
            }
        }
    }
}