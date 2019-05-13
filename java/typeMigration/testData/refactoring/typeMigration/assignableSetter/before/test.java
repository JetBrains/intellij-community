public class Test {

    private int foo;

    public int getFoo() {
        return foo;
    }

    public void setFoo(int foo) {
        this.foo = foo;
    }

    static void m(Test someClass) {
        int someNumber = someClass.getFoo();
        System.out.println(someNumber + 10);

        int someNumber1 = 1123;
        someClass.setFoo(someNumber1);
    }
}