public class X {
    private final Base myDelegate = new Base();
    A myField;
    public void method(Test t) {
         myField = t.getA();
         myField.methodFromA();
         t.getA().methodFromA();
    }
}