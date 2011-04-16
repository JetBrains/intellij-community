package org.jetbrains.ether.dependencyView;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 02.04.11
 * Time: 17:12
 * To change this template use File | Settings | File Templates.
 */
public class PackageNameSelector {
    String buffer = null;
    int index = 0;
    BufferedReader reader = null;
    char saved;
    boolean isSaved = false;

    private void read() {
        try {
            buffer = reader.readLine();
            index = 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private char get() {
        if (buffer == null) {
            return 0;
        } else if (index == buffer.length()) {
            read();
            return '\r';
        }

        final char c = buffer.charAt(index++);

        if (c == 0) {
            return ' ';
        }

        return c;
    }

    private char symbol() {
        if (isSaved) {
            isSaved = false;
            return saved;
        }

        final char c = get();

        switch (c) {
            case '"': {
                loop:
                while (true) {
                    switch (get()) {
                        case 0:
                            return 0;
                        case '"':
                        case '\r':
                            break loop;
                        case '\\':
                            switch (get()) {
                                case 0:
                                    return 0;
                                case '\r':
                                    break loop;
                            }
                    }
                }
                return ' ';
            }

            case '\'': {
                loop:
                while (true) {
                    switch (get()) {
                        case 0:
                            return 0;
                        case '\'':
                        case '\r':
                            break loop;
                        case '\\':
                            switch (get()) {
                                case 0:
                                    return 0;
                                case '\r':
                                    break loop;
                            }
                    }
                }
                return ' ';
            }

            case '/': {
                final char d = get();

                if (d == '/') {
                    loop:
                    while (true) {
                        switch (get()) {
                            case 0:
                                return 0;
                            case '\r':
                                break loop;
                        }
                    }
                    return ' ';
                } else if (d == '*') {
                    loop:
                    while (true) {
                        switch (get()) {
                            case 0:
                                return 0;
                            case '*':
                                inner:
                                while (true) {
                                    switch (get()) {
                                        case 0:
                                            return 0;

                                        case '/':
                                            break loop;
                                        case '*':
                                            continue;
                                        default:
                                            break inner;
                                    }
                                }
                        }
                    }
                    return ' ';
                } else {
                    saved = d;
                    isSaved = true;
                    return c;
                }
            }

            default: {
                return c;
            }
        }
    }

    private void open(final String fileName) {
        try {
            reader = new BufferedReader(new FileReader(new File(fileName)));
            read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String parse() {
        final StringBuffer b = new StringBuffer();
        int state = 0;

        while (true) {
            final char c = symbol();
            switch (c) {
                case 0: {
                    return "";
                }

                default: {
                    switch (state) {
                        case 0: // Initial state
                            if (Character.isLetter(c)) {
                                b.append(c);
                                state = 1;
                            }
                            break;

                        case 1: // Identifier
                            if (Character.isLetter(c)) {
                                b.append(c);
                            } else if (Character.isWhitespace(c)) {
                                final String ident = b.toString();
                                b.setLength(0);
                                if (ident.equals("package")) {
                                    state = 2;
                                } else if (ident.equals("import")) {
                                    return "";
                                }
                            } else {
                                state = 0;
                            }
                            break;

                        case 2: // Post "package<whitespace>
                            if (Character.isWhitespace(c)) {

                            } else if (c == '.') {
                                b.append(File.separatorChar);
                            } else if (c == ';') {
                                return b.toString();
                            } else {
                                b.append(c);
                            }
                    }
                }
            }
        }
    }

    public String get(final String fileName) {
        open(fileName);

        final String result = parse();

        close();

        return result;
    }

//
//    final private static String whiteSpacePattern = "(/\\*.*?\\*/|//[^\\r\\n\\f\\x0B]*)";
//    final private static String stringLiteralPattern = "(\"([^\\\\\"]|\\\\.)*\")";
//    final private static String charLiteralPattern = "('([^\\\\']|\\\\.)*')";
//    final private static Pattern skipPattern = Pattern.compile(whiteSpacePattern + "|" + stringLiteralPattern + "|" + charLiteralPattern, Pattern.DOTALL);
//    final private static Pattern packagePattern = Pattern.compile("\\bpackage\\s+([^;]+);");
//    final private static Pattern spacePattern = Pattern.compile("\\s+");
//    final private static Pattern dotPattern = Pattern.compile("\\.");
//
//    public static String get (final String fileName) throws Exception {
//        //try {
//            final FileChannel channel = new FileInputStream(fileName).getChannel();
//
//            final Matcher skipMatcher = skipPattern.matcher(Charset.forName("ISO-8859-1").newDecoder().decode(channel.map(FileChannel.MapMode.READ_ONLY, 0, (int) channel.size())));
//            final String cleared = skipMatcher.replaceAll(" ");
//
//            System.out.println(cleared);
//
//            final Matcher selecting = packagePattern.matcher(cleared);
//
//            if (selecting.find()) {
//                final String qualifiedName = selecting.group(1);
//                final Matcher spaceRemover = spacePattern.matcher(qualifiedName);
//                final String spaceRemoved = spaceRemover.replaceAll("");
//                final Matcher dotReplacer = dotPattern.matcher(spaceRemoved);
//
//                return dotReplacer.replaceAll("/");
//            } else {
//                return null;
//            }
//        //} catch (IOException e) {
//        //    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//
//        //    return null;
//        //}
//    }


}