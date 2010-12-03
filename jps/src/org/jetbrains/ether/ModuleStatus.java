package org.jetbrains.ether;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 3:40
 * To change this template use File | Settings | File Templates.
 */
public class ModuleStatus {
    private static Pattern myPattern = Pattern.compile("([^ ]+) ([0-9]+) ([0-9]+) ([0-9]+) ([0-9]+)");

    String myName;
    long mySourceStamp;
    long myOutputStamp;
    long myTestSourceStamp;
    long myTestOutputStamp;

    public ModuleStatus(String name, long ss, long os, long tss, long tos) {
        myName = name;
        mySourceStamp = ss;
        myOutputStamp = os;
        myTestSourceStamp = tss;
        myTestOutputStamp = tos;
    }

    public String getName () {
        return myName;
    }

    public String toString () {
        return myName + " " + mySourceStamp + " " + myOutputStamp + " " + myTestSourceStamp + " " + myTestOutputStamp;
    }

    public ModuleStatus(final String s) {
        final Matcher m = myPattern.matcher(s);

        if (m.matches()) {
            myName = m.group(1);
            mySourceStamp = Long.parseLong(m.group(2));
            myOutputStamp = Long.parseLong(m.group(3));
            myTestSourceStamp = Long.parseLong(m.group(4));
            myTestOutputStamp = Long.parseLong(m.group(5));
        }
        else
            System.err.println("Error converting string \"" + s + "\" to ModuleStatus");
    }

    private static boolean wiseCompare (long input, long output) {
        final boolean result = (input > 0 && output == Long.MAX_VALUE) || (output <= input);
        return result;
    }

    public boolean isOutdated(boolean tests) {
        final boolean result = wiseCompare(mySourceStamp, myOutputStamp) || (tests && wiseCompare(myTestSourceStamp, myTestOutputStamp));
        return result;
    }
}
