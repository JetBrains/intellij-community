import java.io.*;

class Test implements Serializable {
  @Serial
  private static final long serialVersionUID = 7874493593505141603L;
  @Serial
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  @Serial
  private void writeObject(ObjectOutputStream out) throws IOException {
  }

  @Serial
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  @Serial
  private void readObjectNoData() throws ObjectStreamException {
  }

  @Serial
  public Object writeReplace() throws ObjectStreamException {
    return 1;
  }

  @Serial
  protected Object readResolve() throws ObjectStreamException {
    return 1;
  }

  public void foo() {
    <error descr="'@Serial' not applicable to local variable">@Serial</error>
    int a = 1;
  }
}

enum Bar {
  ;
  @Serial
  private static final long serialVersionUID = 7874493593505141603L;
  @Serial
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
}