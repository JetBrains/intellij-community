package org.jetbrains.ether;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 29.01.11
 * Time: 21:10
 * To change this template use File | Settings | File Templates.
 */
public class RW {

    public interface Writable {
        public void write(BufferedWriter w);
    }

    public static <T extends Comparable> void writeln(final BufferedWriter w, final Collection<T> c, final ToWritable<T> t) {
        if (c == null) {
            writeln(w, "0");
            return;
        }

        writeln(w, Integer.toString(c.size()));

        for (T e : c) {
            t.convert(e).write(w);
        }
    }

    public static void writeEncodedString(final BufferedWriter w, final String val) {
        final int size = val == null ? 0 : val.length();

        writeln(w, Integer.toString(size));

        for (int i = 0; i < size; i++) {
            writeln(w, Integer.toString((int) val.charAt(i)));
        }
    }

    public static void writeln(final BufferedWriter w, final Collection<? extends Writable> c) {
        if (c == null) {
            writeln(w, "0");
            return;
        }

        writeln(w, Integer.toString(c.size()));

        for (Writable e : c) {
            e.write(w);
        }
    }

    public interface ToWritable<T> {
        public Writable convert(T x);
    }

    public static <T> void writeln(final BufferedWriter w, final T[] c, final ToWritable<T> t) {
        if (c == null) {
            writeln(w, "0");
            return;
        }

        writeln(w, Integer.toString(c.length));

        for (int i = 0; i < c.length; i++) {
            t.convert(c[i]).write(w);
        }
    }

    public static void writeln(final BufferedWriter w, final String s) {
        try {
            if (s == null)
                w.write("");
            else
                w.write(s);
            w.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface Reader<T> {
        public T read(BufferedReader r);
    }

    public static ToWritable<String> fromString = new ToWritable<String>() {
        public Writable convert(final String s) {
            return new Writable() {
                public void write(BufferedWriter w) {
                    writeln(w, s);
                }
            };
        }
    };

    public static ToWritable<Writable> fromWritable = new ToWritable<Writable>() {
        public Writable convert(final Writable w) {
            return w;
        }
    };

    public static Reader<String> myStringReader = new Reader<String>() {
        public String read(final BufferedReader r) {
            try {
                return r.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    };

    public static <T> Collection<T> readMany(final BufferedReader r, final Reader<T> c, final Collection<T> acc) {
        final int size = readInt(r);

        for (int i = 0; i < size; i++) {
            acc.add(c.read(r));
        }

        return acc;
    }

    public static String lookString(final BufferedReader r) {
        try {
            r.mark(256);
            final String s = r.readLine();
            r.reset();

            return s;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void readTag(final BufferedReader r, final String tag) {
        try {
            final String s = r.readLine();

            if (!s.equals(tag))
                System.err.println("Parsing error: expected \"" + tag + "\", but found \"" + s + "\"");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readString(final BufferedReader r) {
        try {
            return r.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String readEncodedString(final BufferedReader r) {
        final StringBuffer b = new StringBuffer();

        final int size = readInt(r);

        for (int i = 0; i < size; i++) {
            final int c = readInt(r);
            b.append((char) c);
        }

        return b.toString();
    }

    public static long readLong(final BufferedReader r) {
        final String s = readString(r);

        try {
            return Long.parseLong(s);
        } catch (Exception n) {
            System.err.println("Parsing error: expected long, but found \"" + s + "\"");
            return 0;
        }
    }

    public static int readInt(final BufferedReader r) {
        final String s = readString(r);

        try {
            return Integer.parseInt(s);
        } catch (Exception n) {
            System.err.println("Parsing error: expected integer, but found \"" + s + "\"");
            return 0;
        }
    }

    public static String readStringAttribute(final BufferedReader r, final String tag) {
        try {
            final String s = r.readLine();

            if (s.startsWith(tag))
                return s.substring(tag.length());

            System.err.println("Parsing error: expected \"" + tag + "\", but found \"" + s + "\"");

            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
