// "Invert 'if' condition" "true"
class A {
    public void foo() {
        <caret>if (1 //comment
                   == 2) {
            System.out.println("true");
        } else {
            System.out.println("false");
        }
    }
}