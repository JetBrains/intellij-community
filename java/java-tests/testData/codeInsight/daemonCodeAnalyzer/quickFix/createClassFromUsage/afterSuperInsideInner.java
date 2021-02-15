// "Create class 'Foo'" "true"
class MyTest {
  {
    Class<? extends Throwable> c = Bar.Foo.class;
  }
}

class Bar {
    public class Foo extends Throwable {
    }
}