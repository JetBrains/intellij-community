package stubs;

public class ObjectStubBase<T extends Stub> implements Stub {
  protected T myParent;

  public T getParentStub() {
    return myParent;
  }
}
