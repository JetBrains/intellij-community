import java.io.*;

class Test implements Externalizable {

  private static final long <warning descr="'serialVersionUID' can be annotated with '@Serial' annotation">serialVersionUID</warning> = 7874493593505141603L;

  public Object <warning descr="'writeReplace()' can be annotated with '@Serial' annotation">writeReplace</warning>() throws ObjectStreamException {
    return 1;
  }

  protected Object <warning descr="'readResolve()' can be annotated with '@Serial' annotation">readResolve</warning>() throws ObjectStreamException {
    return 1;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}

record R() implements Externalizable {
  private static final long <warning descr="'serialVersionUID' can be annotated with '@Serial' annotation">serialVersionUID</warning> = 7874493593505141603L;

  public Object <warning descr="'writeReplace()' can be annotated with '@Serial' annotation">writeReplace</warning>() throws ObjectStreamException {
    return 1;
  }

  protected Object <warning descr="'readResolve()' can be annotated with '@Serial' annotation">readResolve</warning>() throws ObjectStreamException {
    return 1;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}