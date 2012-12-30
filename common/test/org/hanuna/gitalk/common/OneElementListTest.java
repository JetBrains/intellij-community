package org.hanuna.gitalk.common;

import java.util.List;

/**
 * @author erokhins
 */
public class OneElementListTest extends AbstractListTest {
    @Override
    protected List<String> getNewList() {
        return new OneElementList<String>();
    }
}
