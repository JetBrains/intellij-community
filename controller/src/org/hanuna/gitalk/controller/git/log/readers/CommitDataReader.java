package org.hanuna.gitalk.controller.git.log.readers;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

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

}
