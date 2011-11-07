package refactoring.changeSignatureGesture;
interface I {
  void add(<caret>);
}

public class IImpl implements I {
  public void add(){}
}