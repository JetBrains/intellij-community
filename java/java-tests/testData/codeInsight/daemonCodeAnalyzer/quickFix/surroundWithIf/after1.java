// "Surround with 'if (i != null)'" "true-preview"
class A {
    void foo(int x){
        String i = x > 0 ? "" : null;
        <caret>if (i != null) {
            i.hashCode();
        }
    }
}