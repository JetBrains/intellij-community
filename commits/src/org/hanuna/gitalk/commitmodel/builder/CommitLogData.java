package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitLogData {
    private final Hash hash;
    private final List<Hash> parents;
    private final String commitMessage;
    private final String author;
    private final long timeStamp;

    // unmodifiableList parents
    public CommitLogData(@NotNull Hash hash, @NotNull List<Hash> parents, @NotNull String commitMessage,
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
    public List<Hash> getParentsHash() {
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
