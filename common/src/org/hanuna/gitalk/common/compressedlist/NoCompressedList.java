package org.hanuna.gitalk.common.compressedlist;

import org.hanuna.gitalk.common.compressedlist.generator.Generator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class NoCompressedList<T> implements CompressedList<T> {
    private final List<T> calcList;
    private final T first;
    private final Generator<T> generator;
    private int size;

    public NoCompressedList(Generator<T> generator, T first, int size) {
        assert size >= 0 : "bad size";
        calcList = new ArrayList<T>(size);
        this.first = first;
        this.generator = generator;
        this.size = size;
        generate();
    }

    private void generate() {
        calcList.clear();
        calcList.add(first);
        T t = first;
        for (int i = 1; i < size; i++) {
            t = generator.generate(t, 1);
            calcList.add(t);
        }
    }

    @NotNull
    @Override
    public List<T> getList() {
        return Collections.unmodifiableList(calcList);
    }


    @Override
    public void recalculate(@NotNull Replace replace) {
        if (replace == Replace.ID_REPLACE) {
            return;
        }
        if (replace.to() >= size) {
            throw new IllegalArgumentException("Bad replace: " + replace.from() + ", " +
                    + replace.to() + ", " + replace.addElementsCount());
        }
        size = replace.addElementsCount() - replace.removeElementsCount() + size;
        generate();
    }
}
