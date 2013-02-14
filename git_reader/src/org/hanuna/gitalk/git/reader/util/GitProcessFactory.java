package org.hanuna.gitalk.git.reader.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author erokhins
 */
public class GitProcessFactory {
    private final static String DEFAULT_LOG_REQUEST = "git log --all --date-order --sparse --encoding=UTF-8 --full-history";
    private final static String COMMIT_PARENTS_LOG_FORMAT = "--format=%h|-%p";
    private final static String TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT = "--format=%at|-%h|-%p";
    private final static String COMMIT_DATA_LOG_FORMAT = "--format=%h|-%an|-%at|-%s";

    public static Process commitData(@NotNull String commitHash) throws IOException {
        String request = "git log " + commitHash + " --no-walk " + COMMIT_DATA_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process commitDatas(@NotNull String commitHashes) throws IOException {
        String request = "git log " + commitHashes + " --no-walk " + COMMIT_DATA_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process allLog() throws IOException {
        String request = "git log --all --date-order " + COMMIT_PARENTS_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process firstPart(int maxCount) throws IOException {
        String request = DEFAULT_LOG_REQUEST + " --max-count=" + maxCount + " " + TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process logPart(long startTimestamp, int maxCount) throws IOException {
        String restrictions = " --before=" + startTimestamp + " --max-count=" + maxCount + " ";
        String request = DEFAULT_LOG_REQUEST + restrictions + TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }


    public static Process refs() throws IOException {
        String request = "git log --all --no-walk --format=%h%d --decorate=full ";
        return Runtime.getRuntime().exec(request);
    }

}
