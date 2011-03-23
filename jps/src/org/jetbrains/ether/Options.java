package org.jetbrains.ether;

import com.sun.jdi.Value;
import groovy.lang.StringWriterIOException;
import org.apache.tools.ant.types.Description;
import org.omg.CORBA.*;

import javax.swing.*;
import java.awt.*;
import java.lang.Object;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.11.10
 * Time: 14:33
 * To change this template use File | Settings | File Templates.
 */
public class Options {

    public enum ArgumentSpecifier {
        MANDATORY,
        OPTIONAL,
        NONE;
    }

    public abstract interface Argument {
    }

    public static class Switch implements Argument {

    }

    private static Switch SWITCH = new Switch();

    public static class Value implements Argument {
        private final String myValue;

        Value (final String s) {
            myValue = s;
        }

        public String get () {
            return myValue;
        }
    }

    private class Cursor {
        int myPos;
        final int myLength;
        final String[] myArgs;

        Cursor (final String[] args) {
            myArgs = args;
            myPos = 0;
            myLength = myArgs.length;
        }

        public String look () {
            if (myPos >= myLength)
                return null;

            return myArgs [myPos];
        }

        public void shift () {
            myPos++;
        }
        public String lookahead () {
            final String arg = myArgs[myPos+1];
            myPos += 2;

            return arg;
        }

        public boolean nextIsArg () {
            return (myPos < myLength - 1 && ! myArgs[myPos+1].startsWith("-"));
        }

        public boolean endOf () {
            return myPos >= myLength;
        }
    }

    private interface Callback {
        void update  (String key, Argument value);
        void addFree (String value);
        void report  (String note);
    }

    public static class Descriptor {
        private final ArgumentSpecifier myArgumentSpecifier;
        private final String myKey;
        private final String myLong;
        private final String myShort;
        private final String myMemo;

        private final Pattern myLongPattern;

        Descriptor (final String key, final String longForm, final String shortForm, final ArgumentSpecifier as, final String memo) {
            myArgumentSpecifier = as;
            myKey = key;
            myLong = longForm;
            myShort = shortForm;
            myMemo = memo;

            myLongPattern = myLong == null ? null : Pattern.compile("^--" + myLong + "(=(.*))?$");
        }

        public String memo () {
            final StringBuffer buf = new StringBuffer();
            String longArg = "", shortArg = "";

            switch (myArgumentSpecifier) {
                case OPTIONAL:
                    longArg = "[=<argument>]";
                    shortArg = " [<argument>]";
                    break;

                case NONE:
                    longArg = "";
                    shortArg = "";
                    break;

                case MANDATORY:
                    longArg = "=<argument>";
                    shortArg = " <argument>";
                    break;
            }

            buf.append("  --" + myLong + longArg + ", -" + myShort + shortArg + "\n");
            buf.append("          " + myMemo + "\n");

            return buf.toString();
        }

        public boolean proceed (final Cursor cursor, final Callback callback) {
            if (cursor.endOf())
                return false;

            final String arg = cursor.look();

            if (! arg.startsWith("-")) {
                callback.addFree(arg);
                cursor.shift();
                return true;
            }

            if (myShort != null && arg.equals("-" + myShort)) {
                switch (myArgumentSpecifier) {
                    case NONE:
                        callback.update(myKey, SWITCH);
                        break;

                    default:
                        if (cursor.nextIsArg())
                            callback.update(myKey, new Value(cursor.lookahead()));
                        else
                            switch (myArgumentSpecifier) {
                                case OPTIONAL:
                                    callback.update(myKey, SWITCH);
                                    break;
                                case MANDATORY:
                                    callback.report("option \"" + arg + "\" requires an argument, discarding.");
                            }
                };

                cursor.shift();
                return true;
            }

            final Matcher m = myLongPattern.matcher(arg);

            if (myLong != null && m.matches()) {
                final String prm = m.group(2);

                switch (myArgumentSpecifier) {
                    case MANDATORY:
                        if (prm == null)
                            callback.report("option \"" + arg + "\" requires an argument, discarding.");
                        else
                            callback.update(myKey, new Value (prm));
                        break;

                    case NONE:
                        if (prm == null)
                            callback.update(myKey, SWITCH);
                        else
                            callback.report("option \"" + arg + "\" does not take an argument, omitting.");
                        break;

                    case OPTIONAL:
                        callback.update(myKey, prm == null ? SWITCH : new Value(prm));
                        break;
                }

                cursor.shift();
                return true;
            }

            return false;
        }
    }

    private Map<String, Argument> myOptions = new HashMap<String, Argument> ();
    private List<String> myFree;

    private final Descriptor[] myDescriptors;

    Options (final Descriptor[] descrs) {
        myDescriptors = descrs;
    }

    public List<String> parse (final String[] args) {
        myOptions.clear();
        myFree = new ArrayList<String> ();
        final List<String> notes = new ArrayList<String> ();
        final Callback cb = new Callback() {
            public void addFree (final String value) {
                myFree.add(value);
            }
            public void update (final String key, final Argument value) {
                myOptions.put(key, value);
            }
            public void report (final String note) {
                notes.add(note);
            }
        };
        final Cursor cursor = new Cursor(args);

        while (!cursor.endOf()) {
            boolean recognized = false;

            for (int i = 0; i<myDescriptors.length; i++)
               recognized |= myDescriptors[i].proceed(cursor, cb);

            if (!recognized) {
                cb.report("unrecognized option \"" + cursor.look() + "\" omitted.");
                cursor.shift();
            }
        }

        return notes;
    }

    public Argument get (final String name) {
        return myOptions.get(name);
    }

    public List<String> getFree () {
        return myFree;
    }

    public String memo () {
        StringBuffer buf = new StringBuffer ();

        for (int i = 0; i<myDescriptors.length; i++) {
            buf.append(myDescriptors[i].memo() + "\n");
        }

        return buf.toString();
    }
}
