// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n, boolean b) {
        switch (n) {
            case 1 -> {
                if (b) {
                    bar("A");
                }
                else {
                    bar("z");
                }
                bar("o");
            }
            case 2 -> {
                if (b) {
                    bar("B");
                }
                else {
                    bar("z");
                }
                bar("o");
            }
            case 3 -> {
                if (b) {<caret>
                     bar("A");
                }
                else {
                    bar("z");
                }
                bar("o");
            }
        }
    }
    void bar(String s){return s;}
}