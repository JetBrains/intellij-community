// "Surround with 'if ((b ? null : "foo") != null)'" "true"
class A {
    void bar(String s) {}

    void foo(boolean b){
        if ((b ? null : "foo") != null) {
            bar(b ? null : "foo");
        }
    }
}