// "Change signature of 'parseInt(String)' to 'parseInt(int)'" "false"
class A {
    public void foo() {
        <caret>Integer.parseInt(1);
    }
}