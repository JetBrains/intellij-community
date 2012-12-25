package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.controller.git_log.AbstractProcessOutputReader;
import org.hanuna.gitalk.controller.git_log.CommitReader;
import org.hanuna.gitalk.controller.git_log.GitException;
import org.hanuna.gitalk.controller.git_log.RefReader;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;

import java.io.IOException;
import java.util.List;

/**
 * @author erokhins
 */
public class Controller {

    public DataPack prepare() throws IOException, GitException {
        CommitReader commitReader = new CommitReader(new AbstractProcessOutputReader.ProgressUpdater() {
            @Override
            public void startDataRead() {

            }

            @Override
            public void updateCuntReadLine(int countReadLine) {

            }
        });
        List<Commit> commits = commitReader.readLastCommits(6);

        RefReader refReader = new RefReader(new AbstractProcessOutputReader.ProgressUpdater() {

            @Override
            public void startDataRead() {

            }

            @Override
            public void updateCuntReadLine(int countReadLine) {

            }
        });
        List<Ref> allRefs = refReader.readAllRefs();
        RefsModel refsModel = RefsModel.existedCommitRefs(allRefs, commits);
        return new DataPack(refsModel, commits);
    }

}
