package org.hanuna.gitalk.data;

import org.hanuna.gitalk.common.Executor;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface DataLoader {

    public boolean allLogReadied();

    public void readAllLog(@NotNull Executor<String> statusUpdater);

    public void readNextPart(@NotNull Executor<String> statusUpdater);

    @NotNull
    public DataPack getDataPack();
}
