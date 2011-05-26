package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;

import java.util.*;

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
        public Collection<T> added();

        public Collection<T> removed();

        public Collection<Pair<T, Difference>> changed();

        public boolean unchanged();
    }

    public static <T> Specifier<T> make(final Set<T> past, final Set<T> now) {
        if (past == null) {
            final Collection<T> removed = new HashSet<T>();
            final Collection<Pair<T, Difference>> changed = new HashSet<Pair<T, Difference>>();

            return new Specifier<T>() {
                public Collection<T> added() {
                    return now;
                }

                public Collection<T> removed() {
                    return removed;
                }

                public Collection<Pair<T, Difference>> changed() {
                    return changed;
                }

                public boolean unchanged() {
                    return false;
                }
            };
        }

        final Set<T> added = new HashSet<T>(now);

        added.removeAll(past);

        final Set<T> removed = new HashSet<T>(past);

        removed.removeAll(now);

        final Set<Pair<T, Difference>> changed = new HashSet<Pair<T, Difference>>();
        final Set<T> intersect = new HashSet<T>(past);
        final Map<T, T> nowMap = new HashMap<T, T>();

        for (T s : now) {
            if (intersect.contains(s)) {
                nowMap.put(s, s);
            }
        }

        intersect.retainAll(now);

        for (T x : intersect) {
            final T y = nowMap.get(x);

            if (x instanceof Proto) {
                final Proto px = (Proto) x;
                final Proto py = (Proto) y;

                changed.add(new Pair<T, Difference>(x, py.difference(px)));
            }
        }

        return new Specifier<T>() {
            public Collection<T> added() {
                return added;
            }

            public Collection<T> removed() {
                return removed;
            }

            public Collection<Pair<T, Difference>> changed() {
                return changed;
            }

            public boolean unchanged() {
                return changed.isEmpty() && added.isEmpty() && removed.isEmpty();
            }

        };
    }

    public abstract int base();

    public static Difference createBase(final int d) {
        return new Difference() {
            public int base() {
                return d;
            }
        };
    }
}
