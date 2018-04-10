package stubs;

public class StubBase<T> extends ObjectStubBase<StubElement> implements StubElement{
  public StubElement getParentStub() {
    return myParent;
  }
}
