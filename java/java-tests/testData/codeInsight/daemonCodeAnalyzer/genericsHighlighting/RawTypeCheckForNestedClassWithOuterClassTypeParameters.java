abstract class C2<T> {
    public class C3 { }
    public abstract void n(T obj);
}

<error descr="Class 'Main' must either be declared abstract or implement abstract method 'n(T)' in 'C2'">class Main extends C2<C2.C3></error> {
    <error descr="Method does not override method from its superclass">@Override</error>
    public void n(C3 obj) { }
}