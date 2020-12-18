// "Create class 'Foo'" "true"
class MyTest {
  {
    Class<? extends Throwable> c = Bar.Fo<caret>o.class;
  }
}

class Bar {}