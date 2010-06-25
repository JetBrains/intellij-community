// "Surround with 'if (s != null)'" "true"
class A {
    void foo(){
        String s = null;
        for (int i=0; i!=<caret>s.hashCode();i++){}
    }
}