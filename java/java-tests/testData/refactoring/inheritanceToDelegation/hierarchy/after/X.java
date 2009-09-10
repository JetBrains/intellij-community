public class X {
    A myField;
    private final Base myDelegate = new Base();

    public void method(Test t) {
         myField = t.getA();
         myField.methodFromA();
         t.getA().methodFromA();
    }
}