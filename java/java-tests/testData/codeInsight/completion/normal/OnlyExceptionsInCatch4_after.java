class AbcdClass {}
class AbcdException extends Throwable {}

class Foo {
  {
    try { } catch (final AbcdException<caret> e)
  }
}