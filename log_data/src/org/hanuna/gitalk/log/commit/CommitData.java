package org.hanuna.gitalk.log.commit;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitData {
    private final Hash commitHash;
    private final String message;
    private final String author;
    private final long timeStamp;

    public CommitData(Hash commitHash, @NotNull String message, @NotNull String author, long timeStamp) {
        this.commitHash = commitHash;
        this.message = message;
        this.author = author;
        this.timeStamp = timeStamp;
    }

    public Hash getCommitHash() {
        return commitHash;
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
