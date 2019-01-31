// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 2:
                s = "x";
                break;
            case 1:
                s = "x";
                break;
        }
    }
}