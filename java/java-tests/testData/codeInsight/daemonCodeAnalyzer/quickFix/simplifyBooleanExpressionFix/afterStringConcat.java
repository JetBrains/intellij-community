// "Simplify 'b' to true" "true-preview"
class A {
    void foo(boolean b) {
        if (b) {
            String s = "foo" + true + "bar";
        }
        
    }
}