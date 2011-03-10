class AbcdClass {}
class AbcdException extends Throwable {}

class Foo {
  {
    try { } catch (final Abcd<caret> e)
  }
}