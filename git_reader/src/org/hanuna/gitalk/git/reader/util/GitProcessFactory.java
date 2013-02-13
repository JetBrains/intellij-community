package org.hanuna.gitalk.git.reader.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author erokhins
 */
public class GitProcessFactory {
    private final static String COMMIT_PARENTS_LOG_FORMAT = "--format=%h|-%p";
    private final static String TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT = "--format=%ct|-%h|-%p";
    private final static String COMMIT_DATA_LOG_FORMAT = "--format=%h|-%an|-%ct|-%s";

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

    //startDay < lastDay
    public static Process dayInterval(int startDay, int lastDay) throws IOException {
        String daysArg = "--since=" + lastDay + "\\day " + "--until=" + startDay + "\\day ";
        String request = "git log --all " + daysArg + TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    //startDay < lastDay
    public static Process checkEmpty(int startDay) throws IOException {
        String daysArg = "--until=" + startDay + "\\day ";
        String request = "git log --all -n 1 " + daysArg + TIMESTAMP_COMMIT_PARENTS_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process refs() throws IOException {
        String request = "git log --all --no-walk --format=%h%d --decorate=full ";
        return Runtime.getRuntime().exec(request);
    }

}
