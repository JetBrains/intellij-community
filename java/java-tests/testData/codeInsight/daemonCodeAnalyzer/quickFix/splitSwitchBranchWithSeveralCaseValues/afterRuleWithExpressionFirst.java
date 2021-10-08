// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 3 -> s = "x";
            case 1 + 1 -> s = "x";
        }
    }
}