// "Cast to 'java.lang.Object'" "false"
class A {
    void f() {
        var y = new Object();
        var x = new Object() {
            int a = 12;
        };
        x = <caret>y;
    }
}