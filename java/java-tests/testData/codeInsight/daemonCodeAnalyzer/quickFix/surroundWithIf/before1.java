// "Surround with 'if (i != null)'" "true"
class A {
    void foo(){
        String i = null;
        <caret>i.hashCode();
    }
}