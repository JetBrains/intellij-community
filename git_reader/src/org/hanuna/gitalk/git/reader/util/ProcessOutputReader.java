package org.hanuna.gitalk.git.reader.util;

import org.hanuna.gitalk.common.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public class ProcessOutputReader {
    private final Executor<Integer> progressUpdater;
    private final Executor<String> lineAppender;
    private final Object synchObj = new Object();
    private int countReadLine = 0;
    private String errorMsg = null;

    public ProcessOutputReader(@NotNull Executor<Integer> progressUpdater, @NotNull Executor<String> lineAppender) {
        this.progressUpdater = progressUpdater;
        this.lineAppender = lineAppender;
    }

    public ProcessOutputReader(@NotNull Executor<String> lineAppender) {
        this(
                new Executor<Integer>() {
            @Override
            public void execute(Integer key) {

            }
        },
                lineAppender);
    }

    private void incCountReadLine() {
        progressUpdater.execute(countReadLine);
        countReadLine++;
    }

    private void writeErrorMsg(@NotNull String errorMsg) {
        synchronized (synchObj) {
            this.errorMsg = errorMsg;
        }
    }

    @Nullable
    private String readErrorMsg() {
        synchronized (synchObj) {
            return errorMsg;
        }
    }

    private class ErrorListener extends Thread {

        private final BufferedReader errorReader;
        private ErrorListener(InputStream errorStream) {
            errorReader = new BufferedReader(new InputStreamReader(errorStream));
        }

        private void writeErrorMessage(@NotNull String errorMsg) {
            if (!errorMsg.isEmpty()) {
                ProcessOutputReader.this.writeErrorMsg(errorMsg);
            }
        }

        @Override
        public void run() {
            StringBuilder errorStr = new StringBuilder();
            try {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorStr.append(line).append("\n");
                }
                writeErrorMessage(errorStr.toString());
            } catch (IOException e) {
                writeErrorMessage(e.getMessage() + " | " + errorStr.toString());
            }
        }

    }

    public void startRead(@NotNull Process process) throws IOException, GitException {
        ErrorListener errorListener = new ErrorListener(process.getErrorStream());
        errorListener.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            lineAppender.execute(line);
            incCountReadLine();
        }
        try {
            errorListener.join();
        } catch (InterruptedException e) {
            throw new GitException("join InterruptedException: " + e.getMessage());
        }
        String errorMsg = readErrorMsg();
        if (errorMsg != null) {
            throw new GitException(errorMsg);
        }
    }

}
