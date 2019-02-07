// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 1, 3 -> bar("A");
            case 2 -> bar("B");
        }
    }
    void bar(String s){}
}