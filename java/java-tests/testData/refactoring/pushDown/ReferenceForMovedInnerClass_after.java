import static Desc.ClassToMove.F1;
abstract class InlineIssue {

}

class Desc extends InlineIssue {
    public InlineIssue create(ClassToMove e) {
      F1.notify();
      return null;
    }

    public enum ClassToMove { F1 }
}