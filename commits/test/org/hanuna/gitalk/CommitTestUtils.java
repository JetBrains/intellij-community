package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.commitmodel.builder.CommitLogData;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitTestUtils {
    public static String toStr(CommitLogData data) {
        StringBuilder s = new StringBuilder();
        s.append(data.getHash().toStrHash()).append("|-");

        List<Hash> parentsHash = data.getParentsHash();
        if (parentsHash.size() > 0) {
            s.append(parentsHash.get(0).toStrHash());
        }
        for (int i = 1; i < parentsHash.size(); i++) {
            s.append(" ").append(parentsHash.get(i).toStrHash());
        }
        s.append("|-");

        s.append(data.getAuthor()).append("|-");
        s.append(data.getTimeStamp()).append("|-");
        s.append(data.getCommitMessage());
        return s.toString();
    }

    public static String toShortStr(Commit commit) {
        StringBuilder s = new StringBuilder();
        CommitData data = commit.getData();
        if (data == null) {
            throw new IllegalStateException();
        }
        s.append(commit.hash().toStrHash()).append("|-");
        List<Commit> parents = data.getParents();
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
