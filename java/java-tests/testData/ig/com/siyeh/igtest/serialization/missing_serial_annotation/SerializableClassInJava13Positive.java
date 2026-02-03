import java.io.*;

class Test implements Serializable {

  private static final long serialVersionUID = 7874493593505141603L;
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  private void writeObject(ObjectOutputStream out) throws IOException {
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  private void readObjectNoData() throws ObjectStreamException {
  }
}