package org.jetbrains.ether;

import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 18.01.11
 * Time: 6:33
 * To change this template use File | Settings | File Templates.
 */
public class DotPrinter {
    static PrintStream deafultStream;

    private static String escape(final String s) {
        final StringBuffer b = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);

            if (c == '_' || Character.isDigit(c) || Character.isLetter(c)) {
                b.append(c);
            } else if (c == '-') {
                b.append('_');
            } else {
                b.append(Character.getNumericValue(c));
            }
        }

        return b.toString();
    }

    public static void setPrintStream(final PrintStream s) {
        deafultStream = s;
    }

    public static void header(final PrintStream s) {
        if (s != null)
            s.println("digraph X {");
    }

    public static void header() {
        header(deafultStream);
    }

    public static void footer(final PrintStream s) {
        if (s != null)
            s.println("}");
    }

    public static void footer() {
        footer(deafultStream);
    }

    public static void node(final PrintStream s, String n) {
        if (s != null) {
            n = escape(n);
            s.println("  " + n + "[label=\"" + n + "\"];");
        }
    }

    public static void node(final String n) {
        node(deafultStream, n);
    }

    public static void edge(final PrintStream s, String b, String e) {
        if (s != null) {
            b = escape(b);
            e = escape(e);

            s.println("  " + b + " -> " + e + ";");
        }
    }

    public static void edge(final String b, final String e) {
        edge(deafultStream, b, e);
    }
}
