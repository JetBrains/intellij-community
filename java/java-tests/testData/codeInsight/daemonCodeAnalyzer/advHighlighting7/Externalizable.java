import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

class SerializationProxy implements Externalizable
{
  private static final long serialVersionUID = 1L;

  private Object object;

  public SerializationProxy()
  {
    // Empty constructor for Externalizable class
  }

  private <warning descr="Private constructor 'SerializationProxy(java.lang.Object)' is never used">SerializationProxy</warning>(Object object)
  {
    this.object = object;
  }

  public void writeExternal(ObjectOutput out) throws IOException
  {
    out.writeObject(this.object);
  }

  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
  {
    this.object = in.readObject();
  }

  protected Object readResolve()
  {
    return this.object;
  }
}