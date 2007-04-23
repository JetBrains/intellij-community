package com.intellij.localVcs;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.localVcs.LocalVcsItemsLocker;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

public class MockAbstractVcs extends AbstractVcs implements ProjectComponent {
  private boolean myMarkExternalChangesAsCurrent = false;
  private LocalVcsItemsLocker myUpToDateRevisionProvider;
  private CheckinEnvironment myCheckinEnvironment;
  private CommittedChangesProvider myCommittedChangesProvider;

  public MockAbstractVcs(Project project){
    super(project);
  }

  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangesProvider;
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

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public String getComponentName() {
    return "mock";
  }

  public void initComponent() { }

  public void disposeComponent() {
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
}
