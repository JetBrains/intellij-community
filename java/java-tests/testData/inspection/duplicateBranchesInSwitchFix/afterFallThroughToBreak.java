// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 1:
            case 3:
                bar("A");
            case 2:
                break;
            case 4:
                bar("A");
            case 5:
        }
    }
    void bar(String s){}
}