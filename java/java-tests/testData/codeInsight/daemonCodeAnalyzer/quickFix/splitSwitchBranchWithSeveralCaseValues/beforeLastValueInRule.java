// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1, 2<caret> -> s = "x";
        }
    }
}