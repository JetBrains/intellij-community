// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case <caret>1 + 1, 3 -> s = "x";
        }
    }
}