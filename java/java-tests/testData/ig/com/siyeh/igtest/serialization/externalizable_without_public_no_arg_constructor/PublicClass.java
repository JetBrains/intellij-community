import java.io.*;

public class <warning descr="Externalizable class 'PublicClass' has no 'public' no-arg constructor">PublicClass</warning> implements Externalizable {
    
    public PublicClass(int x) {}
    
    @Override public void writeExternal(ObjectOutput out) {}

    @Override public void readExternal(ObjectInput in) { }
}