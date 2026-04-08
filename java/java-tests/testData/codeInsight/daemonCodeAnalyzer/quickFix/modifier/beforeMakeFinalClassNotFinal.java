// "Make 'Magnanimous' not final" "true-preview"
final class Magnanimous {

  protected Magnanimous() {
  }

  final void f() {}
}
class Steadfast extends Magnanimous<caret> {}