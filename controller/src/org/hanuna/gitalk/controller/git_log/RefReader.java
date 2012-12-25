package org.hanuna.gitalk.controller.git_log;

import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class RefReader extends AbstractProcessOutputReader {
    private final List<Ref> refs = new ArrayList<Ref>();

    public RefReader(@NotNull ProgressUpdater progressUpdater) {
        super(progressUpdater);
    }

    @Override
    protected void appendLine(@NotNull String line) {
        refs.add(RefParser.parse(line));
    }

    @NotNull
    public List<Ref> readAllRefs() throws GitException, IOException {
        Process process = GitProcessFactory.refs();
        try {
            startRead(process);
            return refs;
        } catch (InterruptedException e) {
            throw new  IllegalStateException("unaccepted InterruptedException: " + e.getMessage());
        }
    }


}
