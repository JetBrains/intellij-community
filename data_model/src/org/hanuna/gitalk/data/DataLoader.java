package org.hanuna.gitalk.data;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author erokhins
 */
public interface DataLoader {

    public boolean allLogReadied();

    public void readAllLog(@NotNull Executor<String> statusUpdater) throws IOException, GitException;

    public void readNextPart(@NotNull Executor<String> statusUpdater) throws IOException, GitException;

    @NotNull
    public DataPack getDataPack();
}
