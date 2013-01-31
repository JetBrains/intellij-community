package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.OneElementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
class MutableCommit implements Commit {
    private final Hash hash;
    private List<Commit> parents = null;

    public MutableCommit(@NotNull Hash hash) {
        this.hash = hash;
    }

    public void setParents(@NotNull List<Commit> parents) {
        this.parents = OneElementList.shortlyList(parents);
    }

    @NotNull
    @Override
    public Hash hash() {
        return hash;
    }

    @Override
    public List<Commit> getParents() {
        if (parents == null) {
            return null;
        } else {
            return Collections.unmodifiableList(parents);
        }
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj != null && obj instanceof Commit) {
            Commit an = (Commit) obj;
            return hash.equals(an.hash());
        }
        return false;
    }
}
