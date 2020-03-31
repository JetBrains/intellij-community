class C {
    void foo(boolean a, boolean b, boolean c) {
        if (a) {
            if (b) {
                call();
            } // foo
            else if (c) {
                otherCall();
            } // bar
            else {
                thirdCall();
            } // baz
        } else {
            fourthCall();
        } // qux
    }
}