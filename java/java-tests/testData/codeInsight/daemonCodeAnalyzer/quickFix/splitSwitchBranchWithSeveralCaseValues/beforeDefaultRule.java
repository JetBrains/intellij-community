// "Split values of 'switch' branch" "false"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1, 2 -> s = "x";
            <caret>default -> s = "";
        }
    }
}