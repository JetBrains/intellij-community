class C {
    void foo(boolean a, boolean b, boolean c) {
        if (a &<caret>& b) {
            System.out.println("ab");
        } else if(a && c) {
            System.out.println("ac");
        }
    }
}