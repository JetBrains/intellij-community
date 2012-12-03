package org.hanuna.gitalk.common.compressedlist;

/**
 * @author erokhins
 */
public class Replace {
    /**
     * This class describe replace in list or another ordered set's
     * Elements (from, to) will be remove, and add addElementsCount new's element
     * Elements from and to should be exist in list, from != to, and they should not change after this replace
     *
     * Example:
     * Replace(1, 3, 2)
     * before:   10, 20, 30, 40, 50, ...
     * after:    10, 20,  1,  2, 40, 50, ...
     */

    public static Replace buildFromChangeInterval(int oldFrom, int oldTo, int newFrom, int newTo) {
        if (oldFrom != newFrom || oldFrom >= oldTo || newFrom >= newTo) {
            throw new IllegalArgumentException("oldFrom: " + oldFrom + ", oldTo: " + oldTo +
                    ", newFrom: " + newFrom + ", newTo: " + newTo);
        }
        return new Replace(oldFrom, oldTo, newTo - newFrom - 1);
    }

    private final int from;
    private final int to;
    private final int addElementsCount;

    public Replace(int from, int to, int addElementsCount) {
        if (from >= to || addElementsCount < 0 || from < 0) {
            throw new IllegalArgumentException("from: " + from + ", to: " + to +
                    ", addElementsCount: " + addElementsCount);
        }
        this.from = from;
        this.to = to;
        this.addElementsCount = addElementsCount;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public int addElementsCount() {
        return addElementsCount;
    }

    public int removeElementsCount() {
        return to - from - 1;
    }



}
