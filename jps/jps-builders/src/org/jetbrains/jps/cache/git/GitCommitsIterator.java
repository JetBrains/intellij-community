//package org.jetbrains.jps.cache.git;
//
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.vcs.VcsException;
//import com.intellij.util.SmartList;
//import com.intellij.util.containers.ContainerUtil;
//import git4idea.history.GitHistoryUtils;
//import git4idea.repo.GitRepository;
//import org.jetbrains.annotations.NotNull;
//
//import java.util.Iterator;
//import java.util.List;
//import java.util.NoSuchElementException;
//
//public class GitCommitsIterator implements Iterator<String> {
//  private static final Logger LOG = Logger.getInstance(GitCommitsIterator.class);
//
//  private static final int MAX_FETCH_SIZE = 1000;
//  private static final int FETCH_SIZE = 100;
//  private final GitRepository myRepository;
//  private final Project myProject;
//  private int fetchedCount;
//  private List<String> commits;
//  private String remote;
//  private int currentPosition;
//
//  public GitCommitsIterator(@NotNull Project project, @NotNull GitRepository repository, @NotNull String remoteUrl) {
//    myRepository = repository;
//    myProject = project;
//    fetchedCount = 0;
//    remote = remoteUrl;
//    fetchOldCommits();
//  }
//
//  @Override
//  public boolean hasNext() {
//    if (commits.size() > 0) {
//      if (currentPosition < commits.size()) return true;
//      if (fetchedCount >= MAX_FETCH_SIZE) {
//        LOG.info("Exceeded fetch limit for git commits");
//        return false;
//      }
//      fetchOldCommits(commits.get(currentPosition - 1));
//      if (commits.size() > 0) {
//        currentPosition = 0;
//        return true;
//      }
//    }
//    return false;
//  }
//
//  @Override
//  public String next() {
//    if (commits.size() == 0 || currentPosition >= commits.size()) throw new NoSuchElementException();
//    String result = commits.get(currentPosition);
//    currentPosition++;
//    return result;
//  }
//
//  @NotNull
//  public String getRemote() {
//    return remote;
//  }
//
//  private void fetchOldCommits() {
//    fetchOldCommits("");
//  }
//
//  private void fetchOldCommits(String latestCommit) {
//    try {
//      commits =
//        ContainerUtil.map(latestCommit.isEmpty() ? GitHistoryUtils.collectTimedCommits(myProject, myRepository.getRoot(), "-n " + FETCH_SIZE) :
//                          GitHistoryUtils.collectTimedCommits(myProject, myRepository.getRoot(), latestCommit, "-n " + FETCH_SIZE),
//                          it -> it.getId().asString());
//      fetchedCount += commits.size();
//      return;
//    }
//    catch (VcsException e) {
//      LOG.warn("Can't get Git hashes for commits", e);
//    }
//    commits = new SmartList<>();
//  }
//}