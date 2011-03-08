package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.03.11
 * Time: 17:39
 * To change this template use File | Settings | File Templates.
 */
public abstract class Difference {
    public static final int NONE = 0;
    public static final int ACCESS = 1;
    public static final int TYPE = 2;
    public static final int VALUE = 4;
    public static final int SIGNATURE = 8;
    public static final int SUPERCLASS = 16;

    public interface Specifier<T> {
        public Collection<T> added ();
        public Collection<T> removed ();
        public Collection<Pair<T, Difference>> changed();
    }

    public static <T> Specifier<T> make (final Set<T> past, final Set<T> now) {
        final Set<T> added = new HashSet<T> (now);

        added.removeAll(past);

        final Set<T> removed = new HashSet<T> (past);

        removed.removeAll(now);

        final Set<Pair<T, Difference>> changed = new HashSet<Pair<T, Difference>> ();

        return new Specifier<T> () {
            public Collection<T> added () {
                return added;
            }

            public Collection<T> removed () {
                return removed;
            }
            public Collection<Pair<T, Difference>> changed () {
                return changed;
            }
        };
    }

    public static <T> Specifier<T> make (final Map<StringCache.S, T> past, final Map<StringCache.S, T> now) {
        return null;
    }

    public abstract int base ();

    public static Difference createBase (final int d) {
        return new Difference () {
            public int base () {
                return d;
            }
        };
    }
}
