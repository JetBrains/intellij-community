public class A implements Intf {
    public final MyIntf myDelegate = new MyIntf();

    public void method1() {
        myDelegate.method2();
    }

    private class MyIntf implements Intf2 {
        public void method2 () {
            System.out.println("2");
        }
    }
}