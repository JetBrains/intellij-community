package org.hanuna.gitalk.common;

import java.util.ArrayList;

/**
 * @author erokhins
 */
public class RemoveIntervalArrayList<T> extends ArrayList<T> {

    /**
     * removes elements (from, to)
     */
    public void removeInterval(int from, int to) {
        if (to - from < 2) {
            return;
        }
        removeRange(from + 1, to);
    }
}
