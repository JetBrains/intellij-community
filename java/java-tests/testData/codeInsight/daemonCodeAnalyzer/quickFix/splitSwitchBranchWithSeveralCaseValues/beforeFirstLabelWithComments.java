// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            // 1
            <caret>case 1: // 2
            // 3
            case 2: // 4
                // 5
                s = "x"; // 6
                // 7
                break; // 8
            //9
        }
    }
}