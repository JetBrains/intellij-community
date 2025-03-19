import java.io.*;

record R1() implements Serializable {
  @Serial
  private static final long serialVersionUID = 7874493593505141603L;
  @Serial
  private static final ObjectStreamField[] <warning descr="'serialPersistentFields' will be ignored during record serialization">serialPersistentFields</warning> = new ObjectStreamField[0];

  @Serial
  private void <warning descr="'writeObject()' will be ignored during record serialization">writeObject</warning>(ObjectOutputStream out) throws IOException {
  }

  @Serial
  private void <warning descr="'readObject()' will be ignored during record serialization">readObject</warning>(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  @Serial
  private void <warning descr="'readObjectNoData()' will be ignored during record serialization">readObjectNoData</warning>() throws ObjectStreamException {
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

record R2() implements Serializable {
  private static long serialVersionUID = 7874493593505141603L;
  private static ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  void writeObject(ObjectOutputStream out) throws IOException {
  }

  void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  private void readObjectNoData(Object o) throws ObjectStreamException {
  }

  @Serial
  private Object readResolve() throws ObjectStreamException {
    return null;
  }

  @Serial
  private Object writeReplace() throws ObjectStreamException {
    return null;
  }
}