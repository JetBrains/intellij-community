package foo;

class Outer<O> {
  public abstract class Inner { 
    class Inner1 {}
  }

  final class AnoterInner extends Outer<String>.Inner.Inn<caret>er1 { }
}
