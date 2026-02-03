// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1<caret>, 2 -> s = "x";
        }
    }
}