package org.hanuna.gitalk.common;

import java.util.List;

/**
 * @author erokhins
 */
public class ShortListTest extends AbstractListTest {
  @Override
  protected List<String> getNewList() {
    return new ShortList<String>();
  }
}
