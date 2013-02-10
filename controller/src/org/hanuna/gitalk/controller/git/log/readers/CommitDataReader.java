package org.hanuna.gitalk.controller.git.log.readers;

import org.hanuna.gitalk.common.Executor;
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

    public static void main(String[] args) {
        List<CommitData> commitDatas = new CommitDataReader().readCommitsData("4a5d57e aab2eab 2160040 ");
        for (int i = 0; i < commitDatas.size(); i++) {
            CommitData commitData = commitDatas.get(i);
            System.out.println(commitData.getAuthor() + " " + commitData.getMessage());
        }
    }

}
