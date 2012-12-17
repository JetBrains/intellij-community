package org.hanuna.gitalk.commitmodel;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitData {
    private final ReadOnlyList<Commit> parents;
    private final String message;
    private final String author;
    private final long timeStamp;

    public CommitData(@NotNull ReadOnlyList<Commit> parents, @NotNull String message,
                      @NotNull String author, long timeStamp) {
        this.parents = parents;
        this.message = message;
        this.author = author;
        this.timeStamp = timeStamp;
    }

    @NotNull
    public ReadOnlyList<Commit> getParents() {
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
