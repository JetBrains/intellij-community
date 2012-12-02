package org.hanuna.gitalk.common.generatemodel;

/**
 * @author erokhins
 */
public class Replace {
    /**
     * This class describe replace in list or another ordered set's
     * Elements (from, to) must be remove, and add addElementsCount new's element
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
        if (from > to || addElementsCount < 0) {
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
        int k = to - from - 1;
        if (k < 0) {
            return 0;
        } else {
            return k;
        }
    }



}
