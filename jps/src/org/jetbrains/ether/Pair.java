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
}
