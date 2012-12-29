package org.hanuna.gitalk.common;

import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class ShortListTest {

    private String listToStr(List list) {
        StringBuilder s = new StringBuilder();
        for (Object o : list) {
            s.append(o).append("|");
        }
        return s.toString();
    }

    @Test
    public void simple1() {
        List<String> shortList = new ShortList<String>();
        shortList.add("1");
        assertEquals(shortList.size(), 1);
        shortList.get(0);
        assertEquals(listToStr(shortList), "1|");

        shortList.add("3");
        assertEquals(shortList.size(), 2);
        assertEquals(listToStr(shortList), "1|3|");

        shortList.add("7");
        assertEquals(shortList.size(), 3);
        assertEquals(listToStr(shortList), "1|3|7|");

        shortList.add(null);
        assertEquals(shortList.size(), 4);
        assertEquals(listToStr(shortList), "1|3|7|null|");
    }

    @Test
    public void nullElements1() {
        List<String> shortList = new ShortList<String>();
        shortList.add(null);
        assertEquals(shortList.size(), 1);
        assertEquals(listToStr(shortList), "null|");

        shortList.add("3");
        assertEquals(shortList.size(), 2);
        assertEquals(listToStr(shortList), "null|3|");

        shortList.remove(0);
        assertEquals(listToStr(shortList), "3|");
    }

    @Test
    public void nullElements2() {
        List<String> shortList = new ShortList<String>();
        shortList.add("1");
        assertEquals(shortList.size(), 1);
        assertEquals(listToStr(shortList), "1|");

        shortList.add(null);
        assertEquals(shortList.size(), 2);
        assertEquals(listToStr(shortList), "1|null|");

        shortList.remove(1);
        assertEquals(listToStr(shortList), "1|");
    }

    @Test
    public void nullElements3() {
        List<String> shortList = new ShortList<String>();
        shortList.add("1");
        assertEquals(shortList.size(), 1);
        assertEquals(listToStr(shortList), "1|");

        shortList.add(null);
        assertEquals(shortList.size(), 2);
        assertEquals(listToStr(shortList), "1|null|");

        shortList.add(null);
        assertEquals(listToStr(shortList), "1|null|null|");
    }

    @Test
    public void removes() {
        List<String> shortList = new ShortList<String>();
        shortList.add("1");
        shortList.add(null);
        shortList.add("3");
        shortList.add("4");
        shortList.add(null);
        assertEquals(listToStr(shortList), "1|null|3|4|null|");

        shortList.remove(0);
        assertEquals(listToStr(shortList), "null|3|4|null|");

        shortList.remove(0);
        assertEquals(listToStr(shortList), "3|4|null|");

        shortList.remove(2);
        assertEquals(listToStr(shortList), "3|4|");
    }

    @Test
    public void removeTest() {
        List<String> shortList = new ShortList<String>();
        shortList.add("1");
        shortList.add(null);
        assertEquals(listToStr(shortList), "1|null|");

        shortList.remove(null);
        assertEquals(listToStr(shortList), "1|");
    }

}
