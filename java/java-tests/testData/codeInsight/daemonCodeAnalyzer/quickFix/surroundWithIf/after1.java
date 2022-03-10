// "Surround with 'if (i != null)'" "true"
class A {
    void foo(int x){
        String i = x > 0 ? "" : null;
        if (i != null<caret>) {
            i.hashCode();
        }
    }
}