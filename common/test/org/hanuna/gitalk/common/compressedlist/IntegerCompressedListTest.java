package org.hanuna.gitalk.common.compressedlist;

import org.hanuna.gitalk.common.compressedlist.generator.Generator;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author erokhins
 */
public class IntegerCompressedListTest {
    private final int size = 200;
    private final IntegerGenerator generator = new IntegerGenerator(size);

    private final Replace[] simple = {
            new Replace(1, 5, 1),
            new Replace(1, 5, 10),
            new Replace(10, 15, 97)
    };

    private final Replace[] marginal = {
            new Replace(size - 2, size - 1, 0),
            new Replace(size - 4, size - 3, 100),
            new Replace(0, 1, 0),
            new Replace(0, 1, 100),
            new Replace(10, 15, 97)
    };

    @Test
    public void simpleNoCompressedList() {
        runTests(new NoCompressedList<Integer>(generator, size), simple);
    }

    @Test
    public void simpleRuntimeGenerateCompressedList() {
        runTests(new RuntimeGenerateCompressedList<Integer>(generator, size), simple);
    }


    @Test
    public void marginalNoCompressedList() {
        runTests(new NoCompressedList<Integer>(generator, size), marginal);
    }

    @Test
    public void marginalRuntimeGenerateCompressedList() {
        runTests(new RuntimeGenerateCompressedList<Integer>(generator, size), marginal);
    }


    public void runTests(CompressedList<Integer> list, Replace[] replaces) {
        RunCompressedListTest<Integer> runner = new RunCompressedListTest<Integer>(list, generator);
        runner.assertList();

        for (Replace replace : replaces) {
            generator.replace(replace);
            runner.runReplace(replace);
        }
    }


    private class IntegerGenerator implements Generator<Integer> {
        private final List<Integer> list;
        private int replaceCount = 0;

        public IntegerGenerator(int size) {
            this.list = new ArrayList<Integer>(size);
            for (int i = 0; i < size; i++) {
                list.add(i);
            }
        }

        @NotNull
        @Override
        public Integer generate(@NotNull Integer prev, int steps) {
            int prevIndex = list.indexOf(prev);
            assert prevIndex != -1;
            if (prevIndex + steps >= list.size()) {
                throw new NoSuchElementException();
            }
            return list.get(prevIndex + steps);
        }

        @NotNull
        @Override
        public Integer generateFirst() {
            return list.get(0);
        }

        public void replace(Replace replace) {
            replaceCount++;
            int shift = replaceCount * 1000;
            list.subList(replace.from(), replace.to() + 1).clear();
            List<Integer> newLists = new ArrayList<Integer>(replace.addedElementCount());
            for (int i = 0; i < replace.addedElementCount(); i++) {
                newLists.add(shift + i);
            }
            list.addAll(replace.from(), newLists);
        }
    }
}
