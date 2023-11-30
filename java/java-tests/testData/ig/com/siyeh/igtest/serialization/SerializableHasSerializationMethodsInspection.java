package com.siyeh.igtest.serialization;

import java.io.Serializable;

public class SerializableHasSerializationMethodsInspection implements Serializable
{
    private static final long serialVersionUID = 1;

    public static void main(String[] args)
    {
        new SerializableHasSerializationMethodsInspection();
    }
    public SerializableHasSerializationMethodsInspection()
    {
    }
}
