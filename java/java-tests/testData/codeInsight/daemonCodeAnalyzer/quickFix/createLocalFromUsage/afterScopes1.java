// "Create local variable 'local'" "true-preview"
class A {
    public void foo() {
        String local<caret>;
        System.out.println(local);
        {
            local = "";
        }
    }
}