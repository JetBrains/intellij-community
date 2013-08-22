package org.hanuna.gitalk.data;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
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
    List<CommitParents> fullLog = log("1|-3", "2|-4", "3|-4", "4|-5");
    List<? extends CommitParents> firstBlock = log("a|-1", "b|-2");
    Collection<Ref> refs = Arrays.asList(ref("master", "a"), ref("release", "b"));

    List<CommitParents> expected = log("a|-1", "b|-2", "1|-3", "2|-4", "3|-4", "4|-5");

    List<? extends CommitParents> result = new VcsLogJoiner().addCommits(fullLog, firstBlock, refs);

    assertEquals(expected, result);
  }

  private static Ref ref(String name, String hash) {
    return new Ref(Hash.build(hash), name, Ref.RefType.LOCAL_BRANCH);
  }

  @NotNull
  private static List<CommitParents> log(@NotNull String... commits) {
    return ContainerUtil.map(Arrays.asList(commits), new Function<String, CommitParents>() {
      @Override
      public CommitParents fun(String commit) {
        return CommitParser.parseCommitParents(commit);
      }
    });
  }
}
