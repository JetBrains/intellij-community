package a;
import a.b.B;
class Demo {
   public static void main(String[] args) {
        new Demo().doSomething(new B());
    }

    void doSomething(A a) {
        ((B) a).foo();
    }
}