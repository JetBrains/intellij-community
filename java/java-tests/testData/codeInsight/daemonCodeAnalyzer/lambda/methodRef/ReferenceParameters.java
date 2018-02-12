class MyTest {
    interface I<X> {
      X m();
    }
    static <T> T bar() {return null;}
    static {
      I i = MyTest::<String>bar;
    }
}
