// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

/*
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.Collection;

@SuppressWarnings("ALL")

/** Shared implementation code for java.util.concurrent. */
class Helpers {
    private Helpers() {}                // non-instantiable

    /**
     * An implementation of Collection.toString() suitable for classes
     * with locks.  Instead of holding a lock for the entire duration of
     * toString(), or acquiring a lock for each call to Iterator.next(),
     * we hold the lock only during the call to toArray() (less
     * disruptive to other threads accessing the collection) and follows
     * the maxim "Never call foreign code while holding a lock".
     */
    static String collectionToString(Collection<?> c) {
        final Object[] a = c.toArray();
        final int size = a.length;
        if (size == 0)
            return "[]";
        int charLength = 0;

        // Replace every array element with its string representation
        for (int i = 0; i < size; i++) {
            Object e = a[i];
            // Extreme compatibility with AbstractCollection.toString()
            String s = (e == c) ? "(this Collection)" : objectToString(e);
            a[i] = s;
            charLength += s.length();
        }

        return toString(a, size, charLength);
    }

    /**
     * Like Arrays.toString(), but caller guarantees that size > 0,
     * each element with index 0 <= i < size is a non-null String,
     * and charLength is the sum of the lengths of the input Strings.
     */
    static String toString(Object[] a, int size, int charLength) {
        // assert a != null;
        // assert size > 0;

        // Copy each string into a perfectly sized char[]
        // Length of [ , , , ] == 2 * size
        final char[] chars = new char[charLength + 2 * size];
        chars[0] = '[';
        int j = 1;
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                chars[j++] = ',';
                chars[j++] = ' ';
            }
            String s = (String) a[i];
            int len = s.length();
            s.getChars(0, len, chars, j);
            j += len;
        }
        chars[j] = ']';
        // assert j == chars.length - 1;
        return new String(chars);
    }

    /** Optimized form of: key + "=" + val */
    static String mapEntryToString(Object key, Object val) {
        final String k, v;
        final int klen, vlen;
        final char[] chars =
            new char[(klen = (k = objectToString(key)).length()) +
                     (vlen = (v = objectToString(val)).length()) + 1];
        k.getChars(0, klen, chars, 0);
        chars[klen] = '=';
        v.getChars(0, vlen, chars, klen + 1);
        return new String(chars);
    }

    private static String objectToString(Object x) {
        // Extreme compatibility with StringBuilder.append(null)
        String s;
        return (x == null || (s = x.toString()) == null) ? "null" : s;
    }
}
