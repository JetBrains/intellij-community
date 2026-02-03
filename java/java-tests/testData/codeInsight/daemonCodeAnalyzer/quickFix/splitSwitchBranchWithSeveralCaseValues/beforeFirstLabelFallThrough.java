// "Copy 'switch' branch" "false"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            <caret>case 1:
            case 2:
                s = "x";
            case 3:
                System.out.println(s);
        }
    }
}