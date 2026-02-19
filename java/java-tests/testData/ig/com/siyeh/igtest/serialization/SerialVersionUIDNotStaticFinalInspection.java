package com.siyeh.igtest.serialization;

import java.io.*;

public class SerialVersionUIDNotStaticFinalInspection implements Serializable
{
    private long serialVersionUID = 1;

    public SerialVersionUIDNotStaticFinalInspection()
    {
        System.out.println(serialVersionUID);
    }

    private void readObject(ObjectInputStream str)
    {

    }

    private void writeObject(ObjectOutputStream str)
    {

    }
}

