import java.io.*;

@SuppressWarnings("serial")
class Test implements Serializable {

  @Serial
  private static final long serialVersionID = 7874493593505141603L;
  @Serial
  static final long serialVersionUID = 7874493593505141603L;

  @Serial
  private static final ObjectStreamField[] serialPersistentFiels = new ObjectStreamField[0];
  @Serial
  private static ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  @Serial
  private void writeObj(ObjectOutputStream out) throws IOException {
  }
  @Serial
  void writeObject(ObjectOutputStream out) throws IOException {
  }

  @Serial
  private void readObj(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }
  @Serial
  public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  @Serial
  private void readObjNoData() throws ObjectStreamException {
  }
  @Serial
  private void readObjectNoData(Object o) throws ObjectStreamException {
  }
  @Serial
  protected void readObjectNoData() throws ObjectStreamException {
  }

  @Serial
  public Integer writeReplace() throws ObjectStreamException {
    return 1;
  }
  @Serial
  public Object writeReplace(int a) throws ObjectStreamException {
    return 1;
  }

  @Serial
  protected Object readResolve(int a) throws ObjectStreamException {
    return 1;
  }
  @Serial
  protected Integer readResolve() throws ObjectStreamException {
    return 1;
  }
}
