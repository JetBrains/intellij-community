package org.hanuna.gitalk.data;

import com.intellij.vcs.log.TimeCommitParents;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hanuna.gitalk.log.parser.CommitParser.log;
import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogMultiRepoJoinerTest {

  @Test
  public void joinTest() {
    List<TimeCommitParents> first = log("6|-a2|-a0", "3|-a1|-a0", "1|-a0|-");
    List<TimeCommitParents> second = log("4|-b1|-b0", "2|-b0|-");
    List<TimeCommitParents> third = log("7|-c1|-c0", "5|-c0|-");

    List<TimeCommitParents> expected = log("7|-c1|-c0", "6|-a2|-a0", "5|-c0|-", "4|-b1|-b0", "3|-a1|-a0", "2|-b0|-", "1|-a0|-");

    List<TimeCommitParents> joined = new VcsLogMultiRepoJoiner().join(Arrays.asList(first, second, third));

    assertEquals(expected, joined);
  }

}
