package org.hanuna.gitalk.controller.git.log.readers;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.log.commit.Commit;
import org.hanuna.gitalk.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitReader {
    private final ProcessOutputReader outputReader;
    private final List<Commit> commits = new ArrayList<Commit>();

    public CommitReader(@NotNull Executor<Integer> progressUpdater){
        outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
            @Override
            public void execute(String key) {
                appendLine(key);
            }
        });
    }

    protected final void appendLine(@NotNull String line) {
        commits.add(CommitParser.parseParentHashes(line));
    }

    @NotNull
    public List<Commit> readAllCommits() throws GitException, IOException {
        Process process = GitProcessFactory.allLog();
        outputReader.startRead(process);
        return commits;
    }

    @NotNull
    public List<Commit> readLastCommits(int monthCount) throws GitException, IOException {
        Process process = GitProcessFactory.lastDays(monthCount);
        outputReader.startRead(process);
        return commits;
    }


    @NotNull
    public List<Commit> readIntervalCommits(int startDay, int lastDay) throws GitException, IOException {
        Process process = GitProcessFactory.dayInterval(startDay, lastDay);
        outputReader.startRead(process);
        return commits;
    }


}
