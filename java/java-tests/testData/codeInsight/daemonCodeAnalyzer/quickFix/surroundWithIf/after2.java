// "Surround with 'if (s != null)'" "true"
class A {
    void foo(){
        String s = null;
        if (s != null<caret>) {
            for (int i=0; i!=s.hashCode();i++){}
        }
    }
}