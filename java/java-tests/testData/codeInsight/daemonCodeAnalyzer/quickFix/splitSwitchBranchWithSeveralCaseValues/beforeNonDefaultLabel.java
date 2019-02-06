// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            <caret>case 1:
            default:
            case 2:
                s = "x";
                break;
        }
    }
}