package ppp;

import stubs.StubBase;
import stubs.StubElement;

public class Util {
  public void calcParent(StubBase stub) { // using raw type 'StubBase', this is important!
    final StubElement parent = stub.getParentStub();
  }
}

