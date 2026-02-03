import java.lang.Object;

@interface TestFor {
  Class[] testForClass();
}

class Foo {
  @TestFor(testForClass = {Object.class, Aaaaaaaaaaaaaaaaaaaaa.class<caret> } )
  public void foo22() {}
}

class Aaaaaaaaaaaaaaaaaaaaa {}