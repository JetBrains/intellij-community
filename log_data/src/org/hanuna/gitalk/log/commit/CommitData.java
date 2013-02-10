package org.hanuna.gitalk.log.commit;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitData {
    private final String message;
    private final String author;
    private final long timeStamp;

    public CommitData(@NotNull String message, @NotNull String author, long timeStamp) {
        this.message = message;
        this.author = author;
        this.timeStamp = timeStamp;
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
