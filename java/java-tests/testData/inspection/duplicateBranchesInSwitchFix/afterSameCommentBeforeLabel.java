// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            // comment
            case 1:
            case 2:
                bar("A");
                break;
        }
    }
    void bar(String s){}
}