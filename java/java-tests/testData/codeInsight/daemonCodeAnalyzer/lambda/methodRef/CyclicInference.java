class MyTest<E> {
   MyTest(E e) {
   }

   interface I<T> {
       MyTest<T> _(T t);
   }

   static <Y> void bar(Y arg, I<Y> i) {
       i._(arg);
   }

   static <Y> void bar(I<Y> i, Y arg) {
       i._(arg);
   }

   static <Y> void bar(I<Y> i) {
       i._(null);
   }

   public static void main(String[] args) {
       I<String> i = MyTest::new;

       bar("", MyTest<String>::new);
       bar("", MyTest::new);

       bar(MyTest<String>::new, "");
       bar(MyTest::new, "");

       bar(MyTest::new);
       bar(MyTest<String>::new);
   }
}
