// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 1 -> bar("A");<caret>
            case 2 -> bar("B");
            default ->bar("A");
        }
    }
    void bar(String s){}
}