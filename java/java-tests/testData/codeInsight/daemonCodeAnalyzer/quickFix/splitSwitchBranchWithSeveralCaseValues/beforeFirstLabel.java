// "Copy 'switch' branch" "true-preview"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            <caret>case 1:
            case 2:
                s = "x";
                break;
        }
    }
}