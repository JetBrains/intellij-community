package org.hanuna.gitalk.git.reader;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.git.reader.util.GitProcessFactory;
import org.hanuna.gitalk.git.reader.util.ProcessOutputReader;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.log.commit.parents.TimestampCommitParents;
import org.hanuna.gitalk.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitParentsReader {
    private static final int DAY_BLOCK_SIZE = 100;

    private int lastDay = 0;
    private Executor<Integer> progressUpdater;

    private List<CommitParents> nextBlock() throws IOException, GitException {
        final List<TimestampCommitParents> commitParentsList = new ArrayList<TimestampCommitParents>();
        final MyTimer gitThink = new MyTimer("gitThink");
        final MyTimer readTimer = new MyTimer("read commit parents");
        ProcessOutputReader outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
            private boolean wasReadFirstLine = false;
            @Override
            public void execute(String key) {
                if (!wasReadFirstLine) {
                    wasReadFirstLine = true;
                    gitThink.print();
                    readTimer.clear();
                }
                TimestampCommitParents commitParents = CommitParser.parseTimestampParentHashes(key);
                commitParentsList.add(commitParents);
            }
        });
        outputReader.startRead(GitProcessFactory.dayInterval(lastDay, lastDay + DAY_BLOCK_SIZE));
        lastDay = lastDay + DAY_BLOCK_SIZE;
        Collections.sort(commitParentsList, new Comparator<TimestampCommitParents>() {
            @Override
            public int compare(TimestampCommitParents o1, TimestampCommitParents o2) {
                if (o1.getTimestamp() < o2.getTimestamp()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        return Collections.<CommitParents>unmodifiableList(commitParentsList);
    }

    private boolean isEmptyTailLog() throws IOException, GitException {
        final boolean[] flag = {true};
        ProcessOutputReader outputReader = new ProcessOutputReader(new Executor<String>() {
            @Override
            public void execute(String key) {
                flag[0] = false;
            }
        });
        outputReader.startRead(GitProcessFactory.checkEmpty(lastDay));
        return flag[0];
    }

    /**
     * @return empty list, if all commits was readied
     */
    @NotNull
    public List<CommitParents> readNextBlock(final Executor<String> statusUpdater) throws IOException, GitException {
        statusUpdater.execute("Begin load git repository");
        progressUpdater = new Executor<Integer>() {
            @Override
            public void execute(Integer key) {
                statusUpdater.execute("Read " + key + " commits");
            }
        };
        List<CommitParents> commitParentsList = nextBlock();
        if (commitParentsList.isEmpty()) {
            if (isEmptyTailLog()) {
                return Collections.emptyList();
            } else {
                while (commitParentsList.isEmpty()) {
                    commitParentsList = nextBlock();
                }
                return commitParentsList;
            }
        } else {
            return commitParentsList;
        }
    }

}
