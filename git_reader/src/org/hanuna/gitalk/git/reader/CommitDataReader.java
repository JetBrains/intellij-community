package org.hanuna.gitalk.git.reader;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.git.reader.util.GitProcessFactory;
import org.hanuna.gitalk.git.reader.util.ProcessOutputReader;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitDataReader {
    private String line;

    @NotNull
    public CommitData readCommitData(@NotNull String commitHash) {
        try {
            Process process = GitProcessFactory.commitData(commitHash);
            line = null;
            ProcessOutputReader outputReader = new ProcessOutputReader(new Executor<String>() {
                @Override
                public void execute(String key) {
                    if (line != null) {
                        throw new IllegalStateException("unaccepted second line: " + key);
                    }
                    line = key;
                }
            });
            outputReader.startRead(process);
            if (line == null) {
                throw new IllegalStateException("line is not read");
            }
            return CommitParser.parseCommitData(line);
        } catch (IOException e) {
            throw new IllegalStateException();
        } catch (GitException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @NotNull
    public List<CommitData> readCommitsData(@NotNull String commitHashes) {
        try {
            final List<CommitData> commitDatas = new ArrayList<CommitData>();
            ProcessOutputReader outputReader = new ProcessOutputReader(new Executor<String>() {
                @Override
                public void execute(String key) {
                    CommitData data = CommitParser.parseCommitData(key);
                    commitDatas.add(data);
                }
            });
            Process process = GitProcessFactory.commitDatas(commitHashes);
            outputReader.startRead(process);
            return commitDatas;
        } catch (IOException e) {
            throw new IllegalStateException();
        } catch (GitException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

}
