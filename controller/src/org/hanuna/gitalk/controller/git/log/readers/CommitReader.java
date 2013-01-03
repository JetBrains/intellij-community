package org.hanuna.gitalk.controller.git.log.readers;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.builder.CommitListBuilder;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.log.commit.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitReader {
    private final CommitListBuilder builder = new CommitListBuilder();
    private final ProcessOutputReader outputReader;

    public CommitReader(@NotNull Executor<Integer> progressUpdater){
        outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
            @Override
            public void execute(String key) {
                appendLine(key);
            }
        });
    }

    protected final void appendLine(@NotNull String line) {
        builder.append(CommitParser.parseParentHashes(line));
    }

    @NotNull
    public List<Commit> readAllCommits() throws GitException, IOException {
        Process process = GitProcessFactory.allLog();
        try {
            outputReader.startRead(process);
            if (! builder.allCommitsFound()) {
                throw new GitException("Bad commit order. Not found several commits");
            }
            return builder.build();
        } catch (InterruptedException e) {
            throw new  IllegalStateException("unaccepted InterruptedException: " + e.getMessage());
        }
    }

    @NotNull
    public List<Commit> readLastCommits(int monthCount) throws GitException, IOException {
        Process process = GitProcessFactory.lastMonth(monthCount);
        try {
            outputReader.startRead(process);
            return builder.build();
        } catch (InterruptedException e) {
            throw new  IllegalStateException("unaccepted InterruptedException: " + e.getMessage());
        }
    }


}
