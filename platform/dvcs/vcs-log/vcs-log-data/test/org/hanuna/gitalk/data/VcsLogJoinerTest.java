package org.hanuna.gitalk.data;

import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import com.intellij.vcs.log.TimeCommitParents;
import org.hanuna.gitalk.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogJoinerTest {

  @Test
  public void simpleTest() {
    String[] INITIAL = {"4|-a2|-a1", "3|-b1|-a", "2|-a1|-a", "1|-a|-"};
    List<TimeCommitParents> fullLog = log(INITIAL);
      List<? extends TimeCommitParents> firstBlock = log("5|-f|-b1", "6|-e|-a2");
    Collection<Ref> refs = Arrays.asList(ref("master", "e"), ref("release", "f"));

    List<TimeCommitParents> expected = log(ArrayUtil.mergeArrays(new String[]{"6|-e|-a2", "5|-f|-b1"}, INITIAL));

    List<? extends TimeCommitParents> result = new VcsLogJoiner().addCommits(fullLog, firstBlock, refs);

    assertEquals(expected, result);
  }

  private static Ref ref(String name, String hash) {
    return new Ref(Hash.build(hash), name, Ref.RefType.LOCAL_BRANCH);
  }

  @NotNull
  private static List<TimeCommitParents> log(@NotNull String... commits) {
    return ContainerUtil.map(Arrays.asList(commits), new Function<String, TimeCommitParents>() {
      @Override
      public TimeCommitParents fun(String commit) {
        return CommitParser.parseTimestampParentHashes(commit);
      }
    });
  }
}
