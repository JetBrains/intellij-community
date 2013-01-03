package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitTestUtils {

    public static String toShortStr(Commit commit) {
        StringBuilder s = new StringBuilder();
        s.append(commit.hash().toStrHash()).append("|-");
        List<Commit> parents = commit.getParents();
        if (parents == null) {
            throw new IllegalStateException();
        }
        if (parents.size() > 0) {
            s.append(parents.get(0).hash().toStrHash());
        }
        for (int i = 1; i < parents.size(); i++) {
            Commit parent =  parents.get(i);
            s.append(" ").append(parent.hash().toStrHash());
        }

        return s.toString();
    }

    public static String toShortStr(List<Commit> commits) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < commits.size(); i++) {
            s.append(toShortStr(commits.get(i)));
            if (i != commits.size() - 1) {
                s.append("\n");
            }
        }
        return s.toString();
    }
}
