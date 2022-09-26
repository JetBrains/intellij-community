// "Create class 'Foo'" "true-preview"
class MyTest {
  {
    Class<? extends Throwable> c = Bar.Foo.class;
  }
}

class Bar {
    public class Foo extends Throwable {
    }
}