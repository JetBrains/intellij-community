// "Invert 'if' condition" "true"
class A {
    public void foo() {
        <caret>if (!c) {
            continue;
        }//c1
        System.out.println();
    }
}