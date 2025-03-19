import java.io.*;

class Test implements Externalizable {
  @Serial
  private static final long serialVersionUID = 7874493593505141603L;

  @Serial
  public Object writeReplace() throws ObjectStreamException {
    return 1;
  }

  @Serial
  protected Object readResolve() throws ObjectStreamException {
    return 1;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}
