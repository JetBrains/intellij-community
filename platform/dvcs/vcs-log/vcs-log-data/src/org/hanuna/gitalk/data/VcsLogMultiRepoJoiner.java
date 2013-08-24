package org.hanuna.gitalk.data;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimeCommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
class VcsLogMultiRepoJoiner {

  @NotNull
  public List<TimeCommitParents> join(@NotNull Collection<List<TimeCommitParents>> logsFromRepos) {
    int size = 0;
    for (List<TimeCommitParents> repo : logsFromRepos) {
      size += repo.size();
    }
    List<TimeCommitParents> result = new ArrayList<TimeCommitParents>(size);

    Map<TimeCommitParents, Iterator<TimeCommitParents>> nextCommits = ContainerUtil.newHashMap();
    for (List<TimeCommitParents> log : logsFromRepos) {
      Iterator<TimeCommitParents> iterator = log.iterator();
      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    while (!nextCommits.isEmpty()) {
      TimeCommitParents lastCommit = findLatestCommit(nextCommits.keySet());
      Iterator<TimeCommitParents> iterator = nextCommits.get(lastCommit);
      result.add(lastCommit);
      nextCommits.remove(lastCommit);

      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    return result;
  }

  @NotNull
  private static TimeCommitParents findLatestCommit(@NotNull Set<TimeCommitParents> commits) {
    long maxTimeStamp = 0;
    TimeCommitParents lastCommit = null;
    for (TimeCommitParents commit : commits) {
      if (commit.getAuthorTime() > maxTimeStamp) {
        maxTimeStamp = commit.getAuthorTime();
        lastCommit = commit;
      }
    }
    assert lastCommit != null;
    return lastCommit;
  }

}
