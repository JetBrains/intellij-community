// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1 -> s = "x";
            case 2 -> s = "x";
            case 3 -> s = "x";
            <caret>case 4 -> s = "x";
        }
    }
}