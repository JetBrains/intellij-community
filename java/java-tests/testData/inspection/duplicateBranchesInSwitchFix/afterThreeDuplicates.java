// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 1:
            case 4:
                bar("A");
                break;
            case 2:
                bar("B");
                break;
            case 3:
                bar("A");
                break;
        }
    }
    void bar(String s){}
}