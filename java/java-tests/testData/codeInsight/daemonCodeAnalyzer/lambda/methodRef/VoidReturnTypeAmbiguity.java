class App {

    public static void main(String[] args) {
        test(App::getLength);
    }

    private static Integer getLength(String word) {
        return word.length();
    }


    private static void <warning descr="Private method 'test(App.Consumer<java.lang.String>)' is never used">test</warning>(Consumer<String> consumer) {
        consumer.accept("Hello World");
    }

    private static void test(Function<String, Integer> function) {
        Integer result = function.apply("Hello World");
        System.out.println(result);
    }

    interface Function<T, R> {
        R apply(T t);
    }

    interface Consumer<T> {
        void accept(T t);
    }
}

