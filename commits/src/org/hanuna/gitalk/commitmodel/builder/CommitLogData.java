package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitLogData {
    private final Hash hash;
    private final ReadOnlyList<Hash> parents;
    private final String commitMessage;
    private final String author;
    private final long timeStamp;

    public CommitLogData(@NotNull Hash hash, @NotNull ReadOnlyList<Hash> parents, @NotNull String commitMessage,
                         @NotNull String author, long timeStamp) {
        this.hash = hash;
        this.parents = parents;
        this.commitMessage = commitMessage;
        this.author = author;
        this.timeStamp = timeStamp;
    }

    @NotNull
    public Hash getHash() {
        return hash;
    }

    @NotNull
    public ReadOnlyList<Hash> getParentsHash() {
        return parents;
    }

    @NotNull
    public String getCommitMessage() {
        return commitMessage;
    }

    @NotNull
    public String getAuthor() {
        return author;
    }

    public long getTimeStamp() {
        return timeStamp;
    }
}
