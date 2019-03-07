// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1:
            case 2:<caret>
                s = "x";
                break;
        }
    }
}