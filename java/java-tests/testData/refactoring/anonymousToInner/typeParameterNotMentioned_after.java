public class LocalClass {
    <T> void test(T t) {
        Runnable r = new MyClass<>(t);
        r.run();
    }

    private static class MyClass<T> implements Runnable {
        private final T t;

        public MyClass(T t) {
            this.t = t;
        }

        @Override
        public void run() {
            System.out.println(t);
        }
    }
}
