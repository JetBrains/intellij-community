// "Copy 'switch' branch" "true-preview"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1:
            <caret>case default:
            case 2:
                s = "x";
                break;
        }
    }
}