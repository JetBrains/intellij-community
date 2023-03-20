// "Copy 'switch' branch" "true-preview"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1:
            case 3:
                s = "x";
                break;
            <caret>case 2:
                s = "x";
                break;
            case 4:
                s = "y";
        }
    }
}