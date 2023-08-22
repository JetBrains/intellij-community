package com.siyeh.igtest.bugs;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class StreamOpenCloseInspection {
    public void foo() throws FileNotFoundException {
      //  new FileInputStream("bar");
    }

    public void foo2() throws FileNotFoundException {
        final FileInputStream str = new FileInputStream("bar");

    }

    public void foo25() throws FileNotFoundException {
        try {
            final FileInputStream str = new FileInputStream("bar");
        } finally {
        }

    }

    public void foo3() throws IOException {
        final FileInputStream str = new FileInputStream("bar");
        str.close();
    }

    public void foo4() throws IOException {
        FileInputStream str = null;
        try {
            str = new FileInputStream("bar");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        str.close();
    }

    public void foo5() throws IOException {
        FileInputStream str = null;
        try {
            str = new FileInputStream("bar");
        } finally {
            str.close();
        }
    }
    public void foo6() throws IOException {
        FileInputStream str = null;
        try {
            str = new FileInputStream("bar");
        } finally {
        }
    }
}
