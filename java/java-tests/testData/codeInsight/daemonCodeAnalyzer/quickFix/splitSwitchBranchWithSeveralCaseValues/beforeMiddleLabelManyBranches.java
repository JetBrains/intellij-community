// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1:
            <caret>case 2:
            case 3:
                s = "x";
                break;
            case 4:
                s = "y";
        }
    }
}