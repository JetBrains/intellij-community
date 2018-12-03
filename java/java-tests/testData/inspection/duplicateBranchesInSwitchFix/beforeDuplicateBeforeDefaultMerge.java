// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 1:
                bar("A");<caret>
                break;
            case 2:
                bar("B");
                break;
            default:
                bar("A");
                break;
        }
    }
    void bar(String s){}
}