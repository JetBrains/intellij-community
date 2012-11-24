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

    public void setSelect(boolean select) {
        this.select = select;
    }

}
