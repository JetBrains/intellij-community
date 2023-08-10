class MyTest {
   static <T extends Iterable<?>> void print(T collection) {
        for (var item : collection) {
            System.out.println(item);
        }
    }
}