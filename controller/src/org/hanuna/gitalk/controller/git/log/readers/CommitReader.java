package org.hanuna.gitalk.controller.git.log.readers;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.log.commit.CommitParents;
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
    private final List<CommitParents> commitParentses = new ArrayList<CommitParents>();

    public CommitReader(@NotNull Executor<Integer> progressUpdater){
        outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
            @Override
            public void execute(String key) {
                appendLine(key);
            }
        });
    }

    protected final void appendLine(@NotNull String line) {
        commitParentses.add(CommitParser.parseParentHashes(line));
    }

    @NotNull
    public List<CommitParents> readAllCommits() throws GitException, IOException {
        Process process = GitProcessFactory.allLog();
        outputReader.startRead(process);
        return commitParentses;
    }

    @NotNull
    public List<CommitParents> readLastCommits(int monthCount) throws GitException, IOException {
        Process process = GitProcessFactory.lastDays(monthCount);
        outputReader.startRead(process);
        return commitParentses;
    }


    @NotNull
    public List<CommitParents> readIntervalCommits(int startDay, int lastDay) throws GitException, IOException {
        Process process = GitProcessFactory.dayInterval(startDay, lastDay);
        outputReader.startRead(process);
        return commitParentses;
    }


}
