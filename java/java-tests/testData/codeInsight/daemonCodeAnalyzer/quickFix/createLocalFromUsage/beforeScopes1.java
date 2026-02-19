// "Create local variable 'local'" "true-preview"
class A {
    public void foo() {
        System.out.println(local);
        {
            <caret>local = "";
        }
    }
}