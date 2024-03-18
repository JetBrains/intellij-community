class C {
    void foo(int n) {
        String s = "";
        if (n > 0) {
            String <caret>x = "x";
        }
    }
}