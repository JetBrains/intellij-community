package com.siyeh.igtest.serialization;

import java.io.*;

public class ReadObjectAndWriteObjectPrivateInspection implements Serializable
{
    private static final long serialVersionUID = 1;

    public ReadObjectAndWriteObjectPrivateInspection()
    {
    }

    void readObject(ObjectInputStream str)
    {

    }

    public void writeObject(ObjectOutputStream str)
    {

    }
}

