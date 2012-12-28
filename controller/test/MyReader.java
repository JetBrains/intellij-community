import org.hanuna.gitalk.commitmodel.builder.CommitLogData;
import org.hanuna.gitalk.controller.git_log.AbstractProcessOutputReader;
import org.hanuna.gitalk.controller.git_log.GitException;
import org.hanuna.gitalk.controller.git_log.GitProcessFactory;
import org.hanuna.gitalk.parser.GitLogParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MyReader extends AbstractProcessOutputReader {
    private final List<CommitLogData> logDataList = new ArrayList<CommitLogData>();

    public MyReader(ProgressUpdater progressUpdater) {
        super(progressUpdater);
    }

    @Override
    protected void appendLine(@NotNull String line) {
        logDataList.add(GitLogParser.parseCommitData(line));
    }

    @NotNull
    public List<CommitLogData> readAllLogData() throws IOException, GitException, InterruptedException {
        Process process = GitProcessFactory.allLog();
        startRead(process);
        return logDataList;
    }

}
