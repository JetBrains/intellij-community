// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.FactoredExternalizableGraphElement;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.DataInput;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.function.Function;

public class GraphDataInputImpl implements GraphDataInput {
  private static final MethodType ourDefaultReadConstructorType = MethodType.methodType(void.class, GraphDataInput.class);
  private static final MethodHandles.Lookup ourLookup = MethodHandles.lookup();

  private final DataInput myDelegate;

  public GraphDataInputImpl(DataInput delegate) {
    myDelegate = delegate;
  }

  @Override
  public void readFully(byte[] b) throws IOException {
    myDelegate.readFully(b);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException {
    myDelegate.readFully(b, off, len);
  }

  @Override
  public int skipBytes(int n) throws IOException {
    return myDelegate.skipBytes(n);
  }

  @Override
  public boolean readBoolean() throws IOException {
    return myDelegate.readBoolean();
  }

  @Override
  public byte readByte() throws IOException {
    return myDelegate.readByte();
  }

  @Override
  public int readUnsignedByte() throws IOException {
    return myDelegate.readUnsignedByte();
  }

  @Override
  public short readShort() throws IOException {
    return myDelegate.readShort();
  }

  @Override
  public int readUnsignedShort() throws IOException {
    return myDelegate.readUnsignedShort();
  }

  @Override
  public char readChar() throws IOException {
    return myDelegate.readChar();
  }

  @Override
  public int readInt() throws IOException {
    final int val = myDelegate.readUnsignedByte();
    if (val < 192) {
      return val;
    }

    int res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = myDelegate.readUnsignedByte();
      res |= (next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  @Override
  public long readLong() throws IOException {
    final int val = myDelegate.readUnsignedByte();
    if (val < 192) {
      return val;
    }

    long res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = myDelegate.readUnsignedByte();
      res |= (long)(next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  @Override
  public float readFloat() throws IOException {
    return myDelegate.readFloat();
  }

  @Override
  public double readDouble() throws IOException {
    return myDelegate.readDouble();
  }

  @Override
  public String readLine() throws IOException {
    return myDelegate.readLine();
  }

  @Override
  public @NotNull String readUTF() throws IOException {
    return RW.readUTF(myDelegate);
  }

  @Override
  public <T extends ExternalizableGraphElement> T readGraphElement() throws IOException {
    try {
      MethodHandle constructor;
      String className = readUTF();
      Class<?> elemType = Class.forName(className);
      if (FactoredExternalizableGraphElement.class.isAssignableFrom(elemType)) {
        ExternalizableGraphElement factorData = readGraphElement();
        constructor = ourLookup.findConstructor(elemType, MethodType.methodType(void.class, factorData.getClass(), GraphDataInput.class)).bindTo(factorData);
      }
      else {
        constructor = ourLookup.findConstructor(elemType, ourDefaultReadConstructorType);
      }
      //noinspection unchecked
      return processLoadedGraphElement((T)constructor.invoke(this));
    }
    catch(IOException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  @Override
  public <T extends ExternalizableGraphElement, C extends Collection<? super T>> C readGraphElementCollection(C acc) throws IOException {
    try {
      String className = readUTF();
      Class<?> elemType = Class.forName(className);
      
      if (FactoredExternalizableGraphElement.class.isAssignableFrom(elemType)) {
        int groupCount = readInt();
        while (groupCount-- > 0) {
          RW.readCollection(this, new RW.Reader<>() {
            MethodHandle constructor;
            @Override
            public T read() throws IOException {
              try {
                if (constructor == null)  {
                  // first element
                  ExternalizableGraphElement factorData = readGraphElement();
                  constructor = ourLookup.findConstructor(elemType, MethodType.methodType(void.class, factorData.getClass(), GraphDataInput.class)).bindTo(factorData);
                }
                //noinspection unchecked
                return GraphDataInputImpl.this.processLoadedGraphElement((T)constructor.invoke(GraphDataInputImpl.this));
              }
              catch (IOException e) {
                throw e;
              }
              catch (Throwable e) {
                throw new IOException(e);
              }
            }
          }, acc);
        }
        return acc;
      }

      MethodHandle constructor = ourLookup.findConstructor(elemType, ourDefaultReadConstructorType);
      return RW.readCollection(this, () -> {
        try {
          //noinspection unchecked
          return processLoadedGraphElement((T)constructor.invoke(this));
        }
        catch (IOException e) {
          throw e;
        }
        catch (Throwable e) {
          throw new IOException(e);
        }
      }, acc);
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  protected <T extends ExternalizableGraphElement> T processLoadedGraphElement(T element) {
    return element;
  }

  public static GraphDataInput wrap(DataInput in) {
    return in instanceof GraphDataInput? (GraphDataInput)in : new GraphDataInputImpl(in);
  }

  public interface StringEnumerator {
    String toString(int num) throws IOException;
  }

  public static GraphDataInput wrap(DataInput in, @Nullable StringEnumerator enumerator, Function<Object, Object> elementInterner) {
    if (enumerator != null && elementInterner != null) {
      return new GraphDataInputImpl(in) {
        @Override
        public @NotNull String readUTF() throws IOException {
          return (String)elementInterner.apply(enumerator.toString(readInt()));
        }

        @Override
        protected <T extends ExternalizableGraphElement> T processLoadedGraphElement(T element) {
          // by contract interner must return the element of exactly the same type
          //noinspection unchecked
          return (T)elementInterner.apply(super.processLoadedGraphElement(element));
        }
      };
    }

    if (elementInterner != null) {
      return new GraphDataInputImpl(in) {
        @Override
        public @NotNull String readUTF() throws IOException {
          return (String)elementInterner.apply(super.readUTF());
        }

        @Override
        protected <T extends ExternalizableGraphElement> T processLoadedGraphElement(T element) {
          // by contract interner must return the element of exactly the same type
          //noinspection unchecked
          return (T)elementInterner.apply(super.processLoadedGraphElement(element));
        }
      };
    }

    if (enumerator != null) {
      return new GraphDataInputImpl(in) {
        @Override
        public @NotNull String readUTF() throws IOException {
          return enumerator.toString(readInt());
        }
      };
    }
    
    return wrap(in);
  }
}
