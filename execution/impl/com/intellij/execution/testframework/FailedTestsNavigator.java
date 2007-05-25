package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.ui.TestsUIUtil;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.project.Project;

import java.util.List;

public abstract class FailedTestsNavigator implements OccurenceNavigator {
  private static final String NEXT_NAME = ExecutionBundle.message("next.faled.test.action.name");
  private static final String PREVIOUS_NAME = ExecutionBundle.message("prev.faled.test.action.name");

  private Project myProject;

  public FailedTestsNavigator(final Project project) {
    myProject = project;
  }

  public boolean hasNextOccurence() {
    return isInitialized() && getNextOccurenceInfo().hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return isInitialized() && getPreviousOccurenceInfo().hasNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    final FailedTestInfo result = getNextOccurenceInfo();
    selectFailedTest(result);
    return new OccurenceInfo(TestsUIUtil.getOpenFileDescriptor(result.myDefect, myProject, openFailureLine()), result.getDefectNumber(),
                             result.getDefectsCount());
  }

  protected abstract void selectFailedTest(FailedTestInfo result);

  protected abstract boolean openFailureLine();

  protected abstract List<AbstractTestProxy> getAllTests();

  protected abstract AbstractTestProxy getSelectedTest();

  protected abstract boolean isInitialized();

  public abstract void setModel(final TestFrameworkRunningModel model);

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    final FailedTestInfo result = getPreviousOccurenceInfo();
    selectFailedTest(result);
    return new OccurenceInfo(TestsUIUtil.getOpenFileDescriptor(result.myDefect, myProject, openFailureLine()), result.getDefectNumber(),
                             result.getDefectsCount());
  }

  public String getNextOccurenceActionName() {
    return NEXT_NAME;
  }

  public String getPreviousOccurenceActionName() {
    return PREVIOUS_NAME;
  }



  private FailedTestInfo getNextOccurenceInfo() {
    return new NextFailedTestInfo().execute();
  }

  private FailedTestInfo getPreviousOccurenceInfo() {
    return new PreviousFailedTestInfo().execute();
  }



  protected abstract class FailedTestInfo {
    private AbstractTestProxy myDefect = null;
    private List<AbstractTestProxy> myAllTests;
    private List<AbstractTestProxy> myDefects;

    public AbstractTestProxy getDefect() {
      return myDefect;
    }

    private int getDefectNumber() {
      return myDefect == null ? getDefectsCount() : myDefects.indexOf(myDefect);
    }

    public FailedTestInfo execute() {
      myAllTests = getAllTests();
      myDefects = Filter.DEFECTIVE_LEAF.select(myAllTests);
      final int selectionIndex = myAllTests.indexOf(getSelectedTest());
      if (selectionIndex == -1)
        return this;
      final AbstractTestProxy defect = findNextDefect(selectionIndex);
      if (defect == null)
        return this;
      if (defect != getSelectedTest()) {
        myDefect = defect;
        return this;
      }
      final int defectIndex = myDefects.indexOf(defect);
      if (defectIndex == -1 || defectIndex == getBoundIndex())
        return this;
      myDefect = myDefects.get(nextIndex(defectIndex));
      return this;
    }



    private AbstractTestProxy findNextDefect(final int startIndex) {
      for (int i = nextIndex(startIndex); 0 <= i && i < myAllTests.size(); i = nextIndex(i)) {
        final AbstractTestProxy nextDefect = myAllTests.get(i);
        if (Filter.DEFECTIVE_LEAF.shouldAccept(nextDefect))
          return nextDefect;
      }
      return null;
    }

    protected abstract int nextIndex(int defectIndex);

    protected abstract int getBoundIndex();

    protected int getDefectsCount() {
      return myDefects.size();
    }

    private boolean hasNextOccurence() {
      return myDefect != null;
    }
  }

  private class NextFailedTestInfo extends FailedTestInfo {
    protected int nextIndex(final int defectIndex) {
      return defectIndex + 1;
    }

    protected int getBoundIndex() {
      return getDefectsCount() - 1;
    }
  }

  private class PreviousFailedTestInfo extends FailedTestInfo {
    protected int nextIndex(final int defectIndex) {
      return defectIndex - 1;
    }

    protected int getBoundIndex() {
      return 0;
    }
  }
}
