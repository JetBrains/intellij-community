package org.hanuna.gitalk.controller.git_log;

/**
 * @author erokhins
 */
public class GitException extends Exception {
    public GitException(String message) {
        super(message);
    }

    public GitException() {
    }
}
