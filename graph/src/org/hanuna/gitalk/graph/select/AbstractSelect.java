package org.hanuna.gitalk.graph.select;

/**
 * @author erokhins
 */
public class AbstractSelect implements Select {
    private boolean select = false;

    @Override
    public boolean isSelect() {
        return select;
    }

    @Override
    public void select() {
        select = true;
    }

    @Override
    public void unSelect() {
        select = false;
    }
}
