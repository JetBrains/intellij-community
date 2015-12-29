import static InlineIssue.ClassToMove.F1;
abstract class InlineIssue {
  public enum ClassToMove { F1 }

  public InlineIssue cre<caret>ate(ClassToMove e) {
    F1.notify();
    return null;
  }
}

class Desc extends InlineIssue {
}