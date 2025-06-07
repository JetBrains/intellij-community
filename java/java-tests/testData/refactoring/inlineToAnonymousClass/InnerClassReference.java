import java.io.Serializable;

interface Descriptor {
  Serializable createHolder();
}

class ObjectDescriptor implements Descriptor {

  @Override
  public Holder createHolder() {
    return new Holder();
  }

  public class Holder implements Serializable {
  }
}

class IntegerDesc<caret>riptor extends ObjectDescriptor {
}

class Some {
  private final IntegerDescriptor.Holder holder = new IntegerDescriptor().createHolder();
}