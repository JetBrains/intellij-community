// "Surround with 'if (s != null)'" "false"
class A {
    void foo(){
        String s = null;
        for (int i=<caret>s.toString().hashCode(); i==0; ) {}
    }
}
