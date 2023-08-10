package com.siyeh.igtest.serialization;

import java.io.*;

public class SerializableHasSerialVersionUIDInspection implements Serializable
{

    public static void main(String[] args)
    {
        new SerializableHasSerialVersionUIDInspection();
    }
    public SerializableHasSerialVersionUIDInspection()
    {
    }

    private void readObject(ObjectInputStream str)
    {

    }

    private void writeObject(ObjectOutputStream str)
    {

    }
}
