// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 2 -> s = "x";
            <caret>case 1 -> s = "x";
        }
    }
}