// "Split values of 'switch' rule" "false"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1, 2 -> s = "x";
            <caret>default -> s = "";
        }
    }
}