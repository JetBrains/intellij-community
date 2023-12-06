package com.siyeh.igtest.serialization;

import java.io.*;

public class TransientFieldNotInitialized implements Serializable {
    private transient String s = "bas";

    public TransientFieldNotInitialized() {
        this.s = "asdf";
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream(1000);
        final ObjectOutputStream out = new ObjectOutputStream(
                byteOut);
        out.writeObject(new TransientFieldNotInitialized());
        final byte[] bytes = byteOut.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes));
        TransientFieldNotInitialized test = (TransientFieldNotInitialized) in.readObject();
        System.out.println("test: " + test.s);
    }
}
