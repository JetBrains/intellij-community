@interface TestFor {
  Class[] testForClass();
}

class Foo {
  @TestFor(testForClass = { Aaaaaaaaaaaaaaaaaaaaa.class<caret> } )
  public void foo22() {}
}

class Aaaaaaaaaaaaaaaaaaaaa {}