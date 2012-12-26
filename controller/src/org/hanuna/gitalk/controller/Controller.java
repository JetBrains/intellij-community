package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.ProgressUpdater;
import org.hanuna.gitalk.controller.git_log.CommitReader;
import org.hanuna.gitalk.controller.git_log.GitException;
import org.hanuna.gitalk.controller.git_log.RefReader;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.hanuna.gitalk.swing_ui.prgress.ProgressFrame;
import org.hanuna.gitalk.swing_ui.prgress.ProgressModel;

import java.io.IOException;
import java.util.List;

/**
 * @author erokhins
 */
public class Controller {
    private static final String START_MESSAGE = "git think";

    private final ProgressModel progressModel = new ProgressModel();
    private final ProgressFrame frame = new ProgressFrame(progressModel, START_MESSAGE);

    public DataPack prepare() throws IOException, GitException {
        progressModel.setMessage(START_MESSAGE);
        progressModel.setState(ProgressModel.State.UNREFINED_PROGRESS);

        final MyTimer gitThink = new MyTimer("git think");
        final MyTimer commitReadTimer = new MyTimer("commits read");
        CommitReader commitReader = new CommitReader(new ProgressUpdater() {
            @Override
            public void updateFinishedCount(int count) {
                if (count == 0) {
                    gitThink.print();
                    commitReadTimer.clear();
                }
                if (count % 100 == 0) {
                    progressModel.setMessage("read " + count + " commits");
                }
            }
        });
        List<Commit> commits = commitReader.readAllCommits();
        commitReadTimer.print();


        RefReader refReader = new RefReader();
        List<Ref> allRefs = refReader.readAllRefs();
        RefsModel refsModel = RefsModel.existedCommitRefs(allRefs, commits);

        progressModel.setState(ProgressModel.State.HIDE);
        return new DataPack(refsModel, commits);
    }

}
