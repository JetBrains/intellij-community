public class A {
    public static void main(String[] args) {
        B.b();
    }
    private static class B{
        public static B b(){
            return new B();
        }
    }
}
