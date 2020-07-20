// "Simplify 'b' to true" "true"
class A {
    void foo(boolean b) {
        if (b) {
            String s = "foo" + <caret>b + "bar";
        }
        
    }
}