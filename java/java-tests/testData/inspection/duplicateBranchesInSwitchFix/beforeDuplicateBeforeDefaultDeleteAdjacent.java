// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 2:
                bar("B");
                break;
            case 1:
                bar("A");<caret>
                break;
            default:
                bar("A");
                break;
        }
    }
    void bar(String s){}
}