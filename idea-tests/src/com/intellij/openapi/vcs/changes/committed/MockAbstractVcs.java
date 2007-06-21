package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.localVcs.LocalVcsItemsLocker;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.Nullable;

public class MockAbstractVcs extends AbstractVcs {
  private boolean myMarkExternalChangesAsCurrent = false;
  private LocalVcsItemsLocker myUpToDateRevisionProvider;
  private CheckinEnvironment myCheckinEnvironment;
  private CommittedChangesProvider myCommittedChangesProvider;
  private DiffProvider myDiffProvider;

  public MockAbstractVcs(Project project){
    super(project);
  }

  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangesProvider;
  }

  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  public String getName() {
    return "mock";
  }

  public String getDisplayName() {
    return "mock";
  }

  public Configurable getConfigurable() {
    return null;
  }

  public ChangeProvider getChangeProvider() {
    return null;
  }

  public boolean markExternalChangesAsUpToDate() {
    return myMarkExternalChangesAsCurrent ;
  }

  public void setMarkExternalChangesAsCurrent(boolean value){
    myMarkExternalChangesAsCurrent = value;
  }

  public void setUpToDateRevisionProvider(LocalVcsItemsLocker upToDateRevisionProvider) {
    myUpToDateRevisionProvider = upToDateRevisionProvider;
  }

  public LocalVcsItemsLocker getItemsLocker() {
    return myUpToDateRevisionProvider;
  }

  public void setCheckinEnvironment(CheckinEnvironment ce) {
    myCheckinEnvironment = ce;
  }

  public void setCommittedChangesProvider(final CommittedChangesProvider committedChangesProvider) {
    myCommittedChangesProvider = committedChangesProvider;
  }

  public void setDiffProvider(final DiffProvider diffProvider) {
    myDiffProvider = diffProvider;
  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(final String revisionNumberString) {
    return new VcsRevisionNumber.Int(Integer.parseInt(revisionNumberString));
  }
}
