public class A extends BaseClass {
    private final MyBaseInterface myBaseInterface = new MyBaseInterface();

    public A() {
    }

    public A(String s) {
        super(27);
    }

    public void baseInterfaceMethod() {
        myBaseInterface.baseInterfaceMethod();
    }

    private class MyBaseInterface implements BaseInterface {
        public void baseInterfaceMethod() {
            System.out.println("Hi!");
        }
    }
}