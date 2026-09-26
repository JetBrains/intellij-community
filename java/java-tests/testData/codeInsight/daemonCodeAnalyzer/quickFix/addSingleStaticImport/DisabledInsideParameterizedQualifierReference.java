package foo;

class Outer<O> {
  public abstract class Inner { }

  final class FileLockingCallback extends Outer<String>.I<caret>nner { }
}
