// "Split values of 'switch' rule" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1, 2<caret> -> s = "x";
        }
    }
}