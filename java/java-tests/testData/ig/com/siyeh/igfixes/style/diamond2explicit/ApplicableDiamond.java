public class MyClass<T> {}

class Test {
        public static void main(String[] args) {
                MyClass<Integer> anon = new MyClass<<caret>>();
        }
}