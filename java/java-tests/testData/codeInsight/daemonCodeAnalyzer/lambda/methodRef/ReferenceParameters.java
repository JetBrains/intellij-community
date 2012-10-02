class MyTest {
    interface I<X> {
      X _();
    }
    static <T> T bar() {return null;}
    static {
      I i = MyTest::<String>bar;
    }
}
