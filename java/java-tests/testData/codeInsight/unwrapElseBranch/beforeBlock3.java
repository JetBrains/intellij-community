// "Unwrap 'else' branch" "false"

class T {
    void f(boolean b) {
        if (b)
            throw new RuntimeException("When true");
        <caret>else {
            System.out.println("Otherwise");
        }//c1
    }
}