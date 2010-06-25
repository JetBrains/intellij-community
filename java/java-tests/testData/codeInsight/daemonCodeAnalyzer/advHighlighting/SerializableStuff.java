// Serializable/externalizable specific

import java.io.*;
class a implements Serializable {
   private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
       if (stream == null) throw new IOException();
       if (stream == null) throw new ClassNotFoundException();
   }

   private void readObjectNoData() throws ObjectStreamException {
       if (this == null) throw new ObjectStreamException(){};
   }


   private Object readResolve()
                    throws ObjectStreamException {
       if (this == null) throw new ObjectStreamException(){};
       return null;
   }

   private Object writeReplace() { return null; }
   private void writeObject(ObjectOutputStream stream) { if (stream==null) return; }

  
}

class b  {
   private void <warning descr="Private method 'readObject(java.io.ObjectInputStream)' is never used">readObject</warning>(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
       if (stream == null) throw new IOException();
       if (stream == null) throw new ClassNotFoundException();
   }

   private void <warning descr="Private method 'readObjectNoData()' is never used">readObjectNoData</warning>() throws ObjectStreamException {
       if (this == null) throw new ObjectStreamException(){};
   }


   private Object <warning descr="Private method 'readResolve()' is never used">readResolve</warning>()
                    throws ObjectStreamException {
       if (this == null) throw new ObjectStreamException(){};
       return null;
   }

   private Object <warning descr="Private method 'writeReplace()' is never used">writeReplace</warning>() { return null; }
   private void <warning descr="Private method 'writeObject(java.io.ObjectOutputStream)' is never used">writeObject</warning>(ObjectOutputStream stream) { if (stream==null) return; }
}

////////////////////////////
abstract class e implements Externalizable {
}
class eImpl extends e {
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }

    public void writeExternal(ObjectOutput out) throws IOException {
    }
}
class <warning descr="Externalizable class should have public no-args constructor">eImpl1</warning> extends e {
    private eImpl1() {}
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }

    public void writeExternal(ObjectOutput out) throws IOException {
    }
}
class <warning descr="Externalizable class should have public no-args constructor">eImpl2</warning> extends e {
    public eImpl2(int i) {
      System.out.print(i);
    }
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }

    public void writeExternal(ObjectOutput out) throws IOException {
    }
}
