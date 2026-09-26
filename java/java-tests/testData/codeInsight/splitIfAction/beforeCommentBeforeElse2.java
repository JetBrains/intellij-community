class C {
    void foo(boolean a, boolean b, boolean c) {
        if (a &<caret>& b) {
            call();
        } // foo
        else if (a && c) {
            otherCall();
        } // bar
        else if(a) {
            thirdCall();
        } // baz
        else {
            fourthCall();
        } // qux
    }
}