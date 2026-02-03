// multiple extend

import java.io.*;

class c1 {}
class c2 implements Serializable, Externalizable {
    public void writeExternal(ObjectOutput out) throws IOException {
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }
}	
class a <error descr="Class cannot extend multiple classes">extends c1,c2</error> {


}