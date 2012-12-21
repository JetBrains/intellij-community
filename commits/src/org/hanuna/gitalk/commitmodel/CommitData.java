package org.hanuna.gitalk.commitmodel;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitData {
    private final List<Commit> parents;
    private final String message;
    private final String author;
    private final long timeStamp;

    // unmodifiableList parents
    public CommitData(@NotNull List<Commit> parents, @NotNull String message,
                      @NotNull String author, long timeStamp) {
        this.parents = parents;
        this.message = message;
        this.author = author;
        this.timeStamp = timeStamp;
    }

    @NotNull
    public List<Commit> getParents() {
        return parents;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public String getAuthor() {
        return author;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

}
