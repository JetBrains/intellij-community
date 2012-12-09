package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.compressedlist.Replace;

/**
 * @author erokhins
 */
public interface GraphPiece {

    /**
     * after setVisible selected == false
     */
    public boolean visible();
    public Replace setVisible(boolean visible);

    public boolean selected();
    public void setSelected(boolean selected);

}
