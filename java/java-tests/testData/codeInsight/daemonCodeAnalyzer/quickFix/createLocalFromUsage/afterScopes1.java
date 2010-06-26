// "Create Local Variable 'local'" "true"
class A {
    public void foo() {
        String local<caret>;
        System.out.println(local);
        {
            local = "";
        }
    }
}