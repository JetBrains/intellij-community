package org.hanuna.gitalk.common.generatemodel;

import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.common.generatemodel.generator.Generator;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class FullSaveGenerateModel<T> implements GenerateModel<T> {
    private final List<T> calcList;
    private int size = -1;
    private T first;
    private Generator<T> generator;

    public FullSaveGenerateModel() {
        this.calcList = new ArrayList<T>();
    }

    public FullSaveGenerateModel(int size) {
        this.calcList = new ArrayList<T>(size);
    }

    @Override
    public void prepare(Generator<T> generator, T first, int size) {
        assert size >= 0 : "bad size";
        this.first = first;
        this.generator = generator;
        this.size = size;
        generate();
    }

    private void generate() {
        calcList.add(first);
        T t = first;
        for (int i = 1; i < size; i++) {
            t = generator.generate(t, 1);
            calcList.add(t);
        }
    }

    @NotNull
    @Override
    public ReadOnlyList<T> getList() {
        return ReadOnlyList.newReadOnlyList(calcList);
    }


    @Override
    public void update(Interval oldInterval, Interval newInterval) {
        generate();
    }
}
