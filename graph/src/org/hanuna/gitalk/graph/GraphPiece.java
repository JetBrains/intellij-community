package org.hanuna.gitalk.graph;

/**
 * @author erokhins
 */
public interface GraphPiece {

    /**
     * after setVisible selected == false
     */
    public boolean visible();
    public void setVisible(boolean visible);

    public boolean selected();
    public void setSelected(boolean selected);

}
