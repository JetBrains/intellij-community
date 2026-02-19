class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 3 -> <caret>s = "x";
            case 1 + 1 -> s = "x";
        }
    }
}