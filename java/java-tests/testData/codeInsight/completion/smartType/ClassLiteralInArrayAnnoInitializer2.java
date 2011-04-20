import java.lang.Object;

@interface TestFor {
  Class[] testForClass();
}

class Foo {
  @TestFor(testForClass = {Object.class, Aaaaaa<caret> } )
  public void foo22() {}
}

class Aaaaaaaaaaaaaaaaaaaaa {}