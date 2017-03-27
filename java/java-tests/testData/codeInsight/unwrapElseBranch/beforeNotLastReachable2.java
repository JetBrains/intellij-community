// "Unwrap 'else' branch" "false"

class T {
    void m(boolean b) {
        if (b) {
            System.out.println(1);
        }
        <caret>else {
            System.out.println(2);
            return;
        }
        System.out.println(3);
    }
}