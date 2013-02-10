package org.hanuna.gitalk.controller.git.log.readers;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class RefReader {
    private final List<Ref> refs = new ArrayList<Ref>();
    private final ProcessOutputReader outputReader;

    public RefReader(@NotNull Executor<Integer> progressUpdater) {
        outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
            @Override
            public void execute(String key) {
                appendLine(key);
            }
        });
    }

    public RefReader() {
        this(new Executor<Integer>() {
            @Override
            public void execute(Integer key) {

            }
        });
    }

    protected void appendLine(@NotNull String line) {
        refs.addAll(RefParser.parseCommitRefs(line));
    }

    @NotNull
    public List<Ref> readAllRefs() throws GitException, IOException {
        Process process = GitProcessFactory.refs();
        outputReader.startRead(process);
        return refs;
    }


}
