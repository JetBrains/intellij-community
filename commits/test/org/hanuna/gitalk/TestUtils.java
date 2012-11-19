package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.commitmodel.builder.CommitLogData;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;

/**
 * @author erokhins
 */
public class TestUtils {
    public static String toStr(CommitLogData data) {
        StringBuilder s = new StringBuilder();
        s.append(data.getHash().toStrHash()).append("|-");

        final ReadOnlyList<Hash> parentsHash = data.getParentsHash();
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
            throw  new IllegalStateException();
        }
        s.append(data.getLogIndex()).append("|-");
        final ReadOnlyList<Commit> parents = data.getParents();
        if (parents.size() > 0) {
            s.append(parents.get(0).getData().getLogIndex());
        }
        for (int i = 1; i < parents.size(); i++) {
            s.append(" ").append(parents.get(i).getData().getLogIndex());
        }
        s.append("|-");

        s.append(commit.hash().toStrHash());
        return s.toString();
    }

    public static String toShortStr(ReadOnlyList<Commit> commits) {
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
