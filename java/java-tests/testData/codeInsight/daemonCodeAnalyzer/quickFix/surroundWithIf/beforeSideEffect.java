// "Surround with 'if ((Math.random() > 0.5 ? null : "foo") != null)'" "false"
class A {
    void bar(String s) {}

    void foo(boolean b){
        bar(Math.random() > 0.5 ? null<caret> : "foo");
    }
}