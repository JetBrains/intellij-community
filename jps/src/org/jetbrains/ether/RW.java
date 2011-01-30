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
    public static <T> List<T> sort(final Collection<T> coll, final Comparator<? super T> comp) {
        List<T> list = new ArrayList<T>();

        for (T elem : coll) {
            if (elem != null) {
                list.add(elem);
            }
        }

        Collections.sort(list, comp);

        return list;
    }

    public static <T extends Comparable<? super T>> List<T> sort(final Collection<T> coll) {
        return sort(coll, new Comparator<T>() {
            public int compare(T a, T b) {
                return a.compareTo(b);
            }
        });
    }

    public interface Writable extends Comparable {
        public void write(BufferedWriter w);
    }

    public static void writeln(final BufferedWriter w, final Collection<String> c, final String desc) {
        if (c == null) {
            writeln (w, "0");
            return;
        }

        writeln(w, Integer.toString(c.size()));

        if (c instanceof List) {
            for (String e : c) {
                writeln(w, e);
            }
        } else {
            final List<String> sorted = sort(c);

            for (String e : sorted) {
                writeln(w, e);
            }
        }
    }

    public static void writeln(final BufferedWriter w, final Collection<? extends Writable> c) {
        if (c == null) {
                    writeln (w, "0");
                    return;
        }

        writeln(w, Integer.toString(c.size()));

        if (c instanceof List) {
            for (Writable e : c) {
                e.write(w);
            }
        } else {
            final List<? extends Writable> sorted = sort(c);

            for (Writable e : sorted) {
                e.write(w);
            }
        }
    }

    public static void writeln(final BufferedWriter w, final String s) {
        try {
            w.write(s);
            w.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface Constructor<T> {
        public T read(BufferedReader r);
    }

    public static Constructor<String> myStringConstructor = new Constructor<String>() {
        public String read(final BufferedReader r) {
            try {
                return r.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    };

    public static <T> Collection<T> readMany(final BufferedReader r, final Constructor<T> c, final Collection<T> acc) {
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
