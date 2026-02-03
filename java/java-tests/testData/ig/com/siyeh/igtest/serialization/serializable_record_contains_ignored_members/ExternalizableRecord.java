import java.io.*;

record R1() implements Externalizable {
  @Serial
  private static final long serialVersionUID = 7874493593505141603L;
  @Serial
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  @Override
  public void <warning descr="'writeExternal()' will be ignored during record serialization">writeExternal</warning>(ObjectOutput out) throws IOException {
  }

  @Override
  public void <warning descr="'readExternal()' will be ignored during record serialization">readExternal</warning>(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  @Serial
  protected Object readResolve() throws ObjectStreamException {
    return null;
  }

  @Serial
  protected Object writeReplace() throws ObjectStreamException {
    return null;
  }
}