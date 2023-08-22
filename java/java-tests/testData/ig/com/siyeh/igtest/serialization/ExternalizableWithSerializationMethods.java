package com.siyeh.igtest.serialization;

import java.io.*;

public class ExternalizableWithSerializationMethods implements Externalizable
{
    private static final long serialVersionUID = 1;

    private void readObject(ObjectInputStream str)
    {

    }

    private void writeObject(ObjectOutputStream str)
    {

    }

    public void writeExternal(ObjectOutput out) throws IOException {
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }
}
