// "Copy 'switch' branch" "true-preview"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            default:
            case 2:
                s = "x";
                break;
            case 1:
                s = "x";
                break;
        }
    }
}