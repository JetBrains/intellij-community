package org.hanuna.gitalk.common.generatemodel;

import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.RemoveIntervalArrayList;
import org.hanuna.gitalk.common.generatemodel.generator.Generator;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class RuntimeGenerateCompressedList<T> implements CompressedList<T> {
    private final CacheGet<Integer, T> cache = new CacheGet<Integer, T>(new Get<Integer, T>() {
        @Override
        public T get(Integer key) {
            return RuntimeGenerateCompressedList.this.get(key);
        }
    }, 100);

    private final int intervalSave = 20;
    private final RemoveIntervalArrayList<SaveT> listSaveT = new RemoveIntervalArrayList<SaveT>();
    private int size;
    private Generator<T> generator;

    public RuntimeGenerateCompressedList(Generator<T> generator, T firstT, int size) {
        this.size = size;
        this.generator = generator;
        listSaveT.add(new SaveT(0, firstT));
        int curIndex = intervalSave;
        T prevT = firstT;
        while (curIndex < size) {
            prevT = generator.generate(prevT, intervalSave);
            listSaveT.add(new SaveT(curIndex, prevT));
            curIndex = curIndex + intervalSave;
        }
    }

    private int dfs(int tIndex) {
        assert listSaveT.size() > 0;
        int x = 0;
        int y = listSaveT.size() - 1;
        while (y - x > 1) {
            int z = (x + y) / 2;
            if (listSaveT.get(z).getIndex() <= tIndex) {
                x = z;
            } else {
                y = z;
            }
        }
        if (listSaveT.get(y).getIndex() <= tIndex) {
            return y;
        }
        return x;
    }

    @NotNull
    @Override
    public ReadOnlyList<T> getList() {
        return ReadOnlyList.newReadOnlyList(new ReadOnlyList.SimpleAbstractList<T>() {
            @Override
            public T get(int index) {
                return cache.get(index);
            }

            @Override
            public int size() {
                return size;
            }
        });
    }

    @Override
    public void recalculate(@NotNull Replace replace) {
        cache.clear();
        int dIndex = replace.addElementsCount() - replace.removeElementsCount();
        int upSave = dfs(replace.from());
        SaveT upSaveT= listSaveT.get(upSave);

        int downSave = upSave;
        while (downSave < listSaveT.size() && listSaveT.get(downSave).getIndex() < replace.to()) {
            downSave++;
        }
        // fix next t index
        size = size + dIndex;
        for (int i = downSave; i < listSaveT.size(); i++) {
            SaveT saveT = listSaveT.get(i);
            saveT.setIndex(saveT.getIndex() + dIndex);
        }
        int downTIndex = size;
        if (downSave < listSaveT.size()) {
            downTIndex = listSaveT.get(downSave).getIndex();
        }

        // regenerate mediate
        List<SaveT> mediateSave = new ArrayList<SaveT>();
        T prevT = upSaveT.getT();
        int curTIndex = upSaveT.getIndex() + intervalSave;
        while (curTIndex < downTIndex - intervalSave) {
            prevT = generator.generate(prevT, intervalSave);
            mediateSave.add(new SaveT(curTIndex, prevT));
            curTIndex = curTIndex + intervalSave;
        }

        listSaveT.removeInterval(upSave, downSave);
        listSaveT.addAll(upSave + 1, mediateSave);
    }

    private T get(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException();
        }
        int saveIndex = dfs(index);
        final SaveT saveT = listSaveT.get(saveIndex);
        assert index >= saveT.getIndex();
        return generator.generate(saveT.getT(), index - saveT.getIndex());
    }

    private class SaveT {
        private int index;
        private final T t;

        private SaveT(int index, T t) {
            this.index = index;
            this.t = t;
        }

        public int getIndex() {
            return index;
        }

        public T getT() {
            return t;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

}
