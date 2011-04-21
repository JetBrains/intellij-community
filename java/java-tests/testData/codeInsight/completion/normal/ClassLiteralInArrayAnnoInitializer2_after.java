@interface TestFor {
  Class[] testForClass();
}

class Foo {
  @TestFor(testForClass = { Object.class, Aaaaaaaaaaaaaaaaaaaaa<caret> } )
  public void foo22() {}
}

class Aaaaaaaaaaaaaaaaaaaaa {}