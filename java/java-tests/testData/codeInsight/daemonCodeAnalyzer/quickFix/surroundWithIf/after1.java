// "Surround with 'if (i != null)'" "true-preview"
class A {
    void foo(int x){
        String i = x > 0 ? "" : null;
        if (i<caret> != null) {
            i.hashCode();
        }
    }
}