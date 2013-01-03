package org.hanuna.gitalk.controller.git.log.readers;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author erokhins
 */
public class GitProcessFactory {
    private final static String COMMIT_LOG_FORMAT = "--format=%h|-%p";
    private final static String COMMIT_DATA_LOG_FORMAT = "--format=%an|-%ct|-%s";

    public static Process commitData(@NotNull String commitHash) throws IOException {
        String request = "git log " + commitHash + " --no-walk " + COMMIT_DATA_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process allLog() throws IOException {
        String request = "git log --all --date-order " + COMMIT_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process lastMonth(int monthCount) throws IOException {
        String monthArg = "--since=" + monthCount + "\\month\\ago ";
        String request = "git log --all --date-order " + monthArg + COMMIT_LOG_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process refs() throws IOException {
        String request = "git show-ref --head --abbrev";
        return Runtime.getRuntime().exec(request);
    }

}
