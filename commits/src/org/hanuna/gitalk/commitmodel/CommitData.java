package org.hanuna.gitalk.commitmodel;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitData {
    private final int logIndex;
    private final ReadOnlyList<Commit> parents;
    private final String message;
    private final String author;
    private final long timeStamp;

    public CommitData(int logIndex, @NotNull ReadOnlyList<Commit> parents, @NotNull String message,
                      @NotNull String author, long timeStamp) {
        this.logIndex = logIndex;
        this.parents = parents;
        this.message = message;
        this.author = author;
        this.timeStamp = timeStamp;
    }

    public int getLogIndex() {
        return logIndex;
    }

    public ReadOnlyList<Commit> getParents() {
        return parents;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

}
