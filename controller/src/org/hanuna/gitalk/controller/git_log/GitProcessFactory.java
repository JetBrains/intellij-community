package org.hanuna.gitalk.controller.git_log;

import java.io.IOException;

/**
 * @author erokhins
 */
public class GitProcessFactory {
    private final static String LOG_INPUT_FORMAT = "--format=%h|-%p|-%an|-%ct|-%s ";

    public static Process allLog() throws IOException {
        String request = "git log --all --date-order " + LOG_INPUT_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process lastMonth(int monthCount) throws IOException {
        String monthArg = "--since=" + monthCount + "\\month\\ago ";
        String request = "git log --all --date-order " + monthArg + LOG_INPUT_FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static Process refs() throws IOException {
        String request = "git show-ref --head --abbrev";
        return Runtime.getRuntime().exec(request);
    }

}
