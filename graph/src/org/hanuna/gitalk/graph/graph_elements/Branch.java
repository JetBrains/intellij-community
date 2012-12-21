package org.hanuna.gitalk.graph.graph_elements;

import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class Branch {
    private final Commit commit;

    public Branch(Commit commit) {
        this.commit = commit;
    }

    public int getBranchNumber() {
        return commit.hashCode();
    }

}
