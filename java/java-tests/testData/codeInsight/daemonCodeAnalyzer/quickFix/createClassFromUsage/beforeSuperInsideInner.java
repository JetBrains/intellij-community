// "Create class 'Foo'" "true-preview"
class MyTest {
  {
    Class<? extends Throwable> c = Bar.Fo<caret>o.class;
  }
}

class Bar {}