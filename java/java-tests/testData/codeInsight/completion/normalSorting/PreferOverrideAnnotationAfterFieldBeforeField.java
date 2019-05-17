class Foo extends BaseClass {
  int dummyField1;
  <caret>
  int dummyField2;
}

abstract class BaseClass {
  abstract String firstOverride();
}