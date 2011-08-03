// "Change 1 parameter of method parseInt from String to int" "false"
class A {
    public void foo() {
        <caret>Integer.parseInt(1);
    }
}