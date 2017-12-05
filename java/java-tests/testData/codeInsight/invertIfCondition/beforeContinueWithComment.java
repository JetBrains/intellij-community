// "Invert 'if' condition" "true"
class A {
    void f(){
        while (true) {
            i<caret>f (true) {
                continue;//comment
            }
            System.out.println();
        }
    }
}