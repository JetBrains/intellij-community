public class Dot9 {
    public static class A{
        public A(){}
        public void foo(){}
    }

    public static void main(String[] args) {
        new A().<caret>
    }
}
