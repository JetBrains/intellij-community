package org.hanuna.gitalk.controller.git_log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public abstract class AbstractProcessOutputReader {
    private final ProgressUpdater progressUpdater;
    private int countReadLine = 0;
    private String errorMsg = null;

    public AbstractProcessOutputReader(@NotNull ProgressUpdater progressUpdater) {
        this.progressUpdater = progressUpdater;
    }

    private void incCountReadLine() {
        if (countReadLine == 0) {
            progressUpdater.startDataRead();
        } else {
            progressUpdater.updateCuntReadLine(countReadLine);
        }
        countReadLine++;
    }

    private synchronized void writeErrorMsg(@NotNull String errorMsg) {
        this.errorMsg = errorMsg;
    }

    @Nullable
    private synchronized String readErrorMsg() {
        return errorMsg;
    }

    private class ErrorListener extends Thread {

        private final BufferedReader errorReader;
        private ErrorListener(InputStream errorStream) {
            errorReader = new BufferedReader(new InputStreamReader(errorStream));
        }

        private void writeErrorMessage(@NotNull String errorMsg) {
            if (!errorMsg.isEmpty()) {
                AbstractProcessOutputReader.this.writeErrorMsg(errorMsg);
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

    public interface ProgressUpdater {
        public void startDataRead();
        public void updateCuntReadLine(int countReadLine);
    }

    protected abstract void appendLine(@NotNull String line);

    protected void startRead(@NotNull Process process) throws IOException, InterruptedException, GitException {
        ErrorListener errorListener = new ErrorListener(process.getErrorStream());
        errorListener.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            appendLine(line);
            incCountReadLine();
        }
        errorListener.join();
        String errorMsg = readErrorMsg();
        if (errorMsg != null) {
            throw new GitException(errorMsg);
        }
    }
}
