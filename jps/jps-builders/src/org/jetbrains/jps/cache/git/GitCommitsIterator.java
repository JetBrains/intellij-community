package org.jetbrains.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cache.client.JpsNettyClient;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class GitCommitsIterator implements Iterator<String> {
  private static final Logger LOG = Logger.getInstance(GitCommitsIterator.class);

  private static final Object myLock = new Object();
  private static final int MAX_FETCH_SIZE = 1000;
  private final JpsNettyClient myNettyClient;
  private static List<String> repositoryCommits;
  private static String latestBuiltRemoteMasterCommit = "";
  private static String latestDownloadedCommit = "";
  private static int fetchedCount;
  private int currentPosition;
  private String remote;

  public GitCommitsIterator(@NotNull JpsNettyClient nettyClient, @NotNull String remoteUrl) {
    myNettyClient = nettyClient;
    fetchedCount = 0;
    remote = remoteUrl;
    fetchOldCommits();
  }

  public @NotNull String getLatestBuiltRemoteMasterCommit() {
    return latestBuiltRemoteMasterCommit;
  }

  public @NotNull String getLatestDownloadedCommit() {
    return latestDownloadedCommit;
  }

  @Override
  public boolean hasNext() {
    if (repositoryCommits.size() > 0) {
      if (currentPosition < repositoryCommits.size()) return true;
      if (fetchedCount >= MAX_FETCH_SIZE) {
        LOG.info("Exceeded fetch limit for git commits");
        return false;
      }
      fetchOldCommits(repositoryCommits.get(currentPosition - 1));
      if (repositoryCommits.size() > 0) {
        currentPosition = 0;
        return true;
      }
    }
    return false;
  }

  @Override
  public String next() {
    if (repositoryCommits.size() == 0 || currentPosition >= repositoryCommits.size()) throw new NoSuchElementException();
    String result = repositoryCommits.get(currentPosition);
    currentPosition++;
    return result;
  }

  @NotNull
  public String getRemote() {
    return remote;
  }

  private void fetchOldCommits() {
    fetchOldCommits("");
  }

  private void fetchOldCommits(String latestCommit) {
    synchronized (myLock) {
      try {
        // TODO:: FIX awaiting
        repositoryCommits = new SmartList<>();
        myNettyClient.requestRepositoryCommits(latestCommit);
        myLock.wait();
      }
      catch (InterruptedException e) {
        LOG.warn("Can't request repository commits", e);
      }
    }
  }

  public static void setRepositoryCommits(List<String> commits, String latestRemoteMasterCommit, String downloadedCommit) {
    synchronized (myLock) {
      repositoryCommits = commits;
      fetchedCount += repositoryCommits.size();
      if (!downloadedCommit.isEmpty()) latestDownloadedCommit = downloadedCommit;
      if (!latestRemoteMasterCommit.isEmpty()) latestBuiltRemoteMasterCommit = latestRemoteMasterCommit;
      myLock.notify();
    }
  }
}