package org.hanuna.gitalk.controller.git_log;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.builder.CommitListBuilder;
import org.hanuna.gitalk.parser.GitLogParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitReader extends AbstractProcessOutputReader {
    private final CommitListBuilder builder = new CommitListBuilder();

    public CommitReader(@NotNull ProgressUpdater progressUpdater){
        super(progressUpdater);
    }

    @Override
    protected final void appendLine(@NotNull String line) {
        builder.append(GitLogParser.parseCommitData(line));
    }

    public List<Commit> readAllCommits() throws GitException, IOException {
        Process process = GitProcessFactory.allLog();
        try {
            startRead(process);
            if (! builder.allCommitsFound()) {
                throw new GitException("Bad commit order. Not found several commits");
            }
            return builder.build();
        } catch (InterruptedException e) {
            throw new  IllegalStateException("unaccepted InterruptedException: " + e.getMessage());
        }
    }

    public List<Commit> readLastCommits(int monthCount) throws GitException, IOException {
        Process process = GitProcessFactory.lastMonth(monthCount);
        try {
            startRead(process);
            return builder.build();
        } catch (InterruptedException e) {
            throw new  IllegalStateException("unaccepted InterruptedException: " + e.getMessage());
        }
    }

    public List<Commit> readLastCommits() throws GitException, IOException {
        return readLastCommits(6);
    }

}
