// "Create local variable 'local'" "true"
class A {
    public void foo() {
        System.out.println(local);
        {
            <caret>local = "";
        }
    }
}