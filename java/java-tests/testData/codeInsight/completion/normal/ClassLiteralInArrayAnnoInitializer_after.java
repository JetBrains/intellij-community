@interface TestFor {
  Class[] testForClass();
}

class Foo {
  @TestFor(testForClass = { Aaaaaaaaaaaaaaaaaaaaa<caret> } )
  public void foo22() {}
}

class Aaaaaaaaaaaaaaaaaaaaa {}