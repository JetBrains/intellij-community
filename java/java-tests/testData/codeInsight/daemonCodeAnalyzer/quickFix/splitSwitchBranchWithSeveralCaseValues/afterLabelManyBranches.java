// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1:
                s = "x";
                break;
            case 2:
                s = "x";
                break;
            case 4:
                s = "y";
        }
    }
}