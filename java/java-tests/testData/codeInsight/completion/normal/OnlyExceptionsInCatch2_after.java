class AbcdClass {}
class AbcdException extends Throwable {}

class Foo {
  {
    try { } catch (AbcdException<caret> e)
  }
}