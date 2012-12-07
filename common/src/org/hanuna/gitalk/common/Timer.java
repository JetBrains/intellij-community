package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class Timer {
    private long timestamp = System.currentTimeMillis();
    private String message = "timer:";

    public Timer() {}

    public Timer(@NotNull String message) {
        this.message = message;
    }

    public void clear() {
        timestamp = System.currentTimeMillis();
    }

    public long get() {
        return System.currentTimeMillis() - timestamp;
    }

    public void print() {
        long ms = get();
        System.out.println(message + ":" + ms);
    }
}
