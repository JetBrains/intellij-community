@interface TestFor {
  Class[] testForClass();
}

class Foo {
  @TestFor(testForClass = { Aaaaaa<caret> } )
  public void foo22() {}
}

class Aaaaaaaaaaaaaaaaaaaaa {}