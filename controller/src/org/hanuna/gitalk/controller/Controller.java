package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.controller.git.log.CacheCommitDataGetter;
import org.hanuna.gitalk.controller.git.log.readers.CommitReader;
import org.hanuna.gitalk.controller.git.log.readers.GitException;
import org.hanuna.gitalk.controller.git.log.readers.RefReader;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.swing_ui.ErrorFrame;
import org.hanuna.gitalk.swing_ui.GitAlkUI;
import org.hanuna.gitalk.swing_ui.progress.ProgressFrame;
import org.hanuna.gitalk.swing_ui.progress.ProgressModel;
import org.hanuna.gitalk.ui_controller.DataPack;
import org.hanuna.gitalk.ui_controller.UI_Controller;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class Controller {
    private static final String START_MESSAGE = "git think";

    private static final ProgressModel progressModel = new ProgressModel();
    private final ProgressFrame frame = new ProgressFrame(progressModel, START_MESSAGE);

    private static int lastDay = 170;
    private static int incCount = 150;

    public static List<CommitParents> readNextCommits() {
        try {
            List<CommitParents> commitParentses = Collections.emptyList();
            commitParentses = readCommits(incCount, lastDay);
            lastDay += incCount;
            return commitParentses;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GitException e) {
            e.printStackTrace();
        }
        throw new IllegalStateException();
    }

    public static List<CommitParents> readCommits(int dayCount, int startDay) throws IOException, GitException {
        final MyTimer gitThink = new MyTimer("git think");
        final MyTimer commitReadTimer = new MyTimer("commits read");
        CommitReader commitReader = new CommitReader(new Executor<Integer>() {
            @Override
            public void execute(Integer key) {
                if (key == 0) {
                    gitThink.print();
                    commitReadTimer.clear();
                }
                if (key % 100 == 0) {
                    progressModel.setMessage("read " + key + " commits");
                }
            }
        });
        List<CommitParents> commitParentses;
        if (dayCount == 0) {
            commitParentses = commitReader.readAllCommits();
        } else {
            if (startDay == 0) {
                commitParentses = commitReader.readLastCommits(dayCount);
            } else {
                commitParentses = commitReader.readIntervalCommits(startDay, startDay + dayCount);
            }
        }
        commitReadTimer.print();
        return commitParentses;
    }


    public DataPack readData(int monthCount, int startDay) throws IOException, GitException {
        progressModel.setMessage(START_MESSAGE);
        progressModel.setState(ProgressModel.State.UNREFINED_PROGRESS);

        List<CommitParents> commitParentses = readCommits(monthCount, startDay);
        RefReader refReader = new RefReader();
        List<Ref> allRefs = refReader.readAllRefs();
        RefsModel refsModel = new RefsModel(allRefs);

        progressModel.setMessage("graph build");
        DataPack dataPack = new DataPack(refsModel, commitParentses, new CacheCommitDataGetter());
        progressModel.setState(ProgressModel.State.HIDE);

        return dataPack;
    }

    public void run() throws IOException {
        DataPack dataPack;
        try {
            dataPack = readData(lastDay, 0);
        } catch (GitException e) {
            progressModel.setState(ProgressModel.State.HIDE);
            new ErrorFrame(e.getMessage());
            return;
        }
        UI_Controller UIController = new UI_Controller(dataPack);
        GitAlkUI ui = new GitAlkUI(UIController);
        ui.showUi();
    }




}
