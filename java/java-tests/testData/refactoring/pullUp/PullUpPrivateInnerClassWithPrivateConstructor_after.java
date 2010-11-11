public class B extends A {
    private void f(){
        new C();
    }

}

//A.java
class A {
    protected static class C{
        protected C(){

        }
    }
}