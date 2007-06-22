package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public abstract class ChangeReverterTestCase extends IntegrationTestCase {
  protected void revertLastChange(VirtualFile f) throws IOException {
    revertChange(f, 0);
  }

  protected void revertChange(VirtualFile f, int index) throws IOException {
    createReverter(f, index).revert();
  }

  protected ChangeReverter createReverter(VirtualFile f, int index) {
    List<Revision> rr = getVcsRevisionsFor(f);
    return new ChangeReverter(getVcs(), gateway, rr.get(index).getCauseChange());
  }

  protected void assertCanRevert(VirtualFile f, int changeIndex) throws IOException {
    Reverter r = createReverter(f, changeIndex);
    assertTrue(r.checkCanRevert().isEmpty());
  }

  protected void assertCanNotRevert(VirtualFile f, int changeIndex, String error) throws IOException {
    List<String> errors = getCanRevertErrors(f, changeIndex);

    assertEquals(1, errors.size());
    assertEquals(error, errors.get(0));
  }

  protected List<String> getCanRevertErrors(VirtualFile f, int changeIndex) throws IOException {
    Reverter r = createReverter(f, changeIndex);
    return r.checkCanRevert();
  }
}
