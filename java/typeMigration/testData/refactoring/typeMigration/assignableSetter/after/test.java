public class Test {

    private long foo;

    public long getFoo() {
        return foo;
    }

    public void setFoo(long foo) {
        this.foo = foo;
    }

    static void m(Test someClass) {
        long someNumber = someClass.getFoo();
        System.out.println(someNumber + 10);

        int someNumber1 = 1123;
        someClass.setFoo(someNumber1);
    }
}