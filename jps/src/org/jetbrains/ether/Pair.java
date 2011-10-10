package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.06.11
 * Time: 16:48
 * To change this template use File | Settings | File Templates.
 */
public class Pair<X, Y> {
    public final X fst;
    public final Y snd;

    public Pair(final X fst, final Y snd) {
        this.fst = fst;
        this.snd = snd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair pair = (Pair) o;

        if (!fst.equals(pair.fst)) return false;
        if (!snd.equals(pair.snd)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = fst.hashCode();
        result = 31 * result + snd.hashCode();
        return result;
    }
}
