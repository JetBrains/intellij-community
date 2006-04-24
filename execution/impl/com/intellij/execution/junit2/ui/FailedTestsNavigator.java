package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.Filter;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.pom.Navigatable;

import java.util.List;

public class FailedTestsNavigator implements OccurenceNavigator {
  private JUnitRunningModel myModel;
  private static final String NEXT_NAME = ExecutionBundle.message("next.faled.test.action.name");
  private static final String PREVIOUS_NAME = ExecutionBundle.message("prev.faled.test.action.name");

  public boolean hasNextOccurence() {
    return myModel != null && getNextOccurenceInfo().hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myModel != null && getPreviousOccurenceInfo().hasNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    final FailedTestInfo result = getNextOccurenceInfo();
    myModel.actuallySelectTest(result.getDefect());
    return result.getOccurenceInfo();
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    final FailedTestInfo result = getPreviousOccurenceInfo();
    myModel.actuallySelectTest(result.getDefect());
    return result.getOccurenceInfo();
  }

  public String getNextOccurenceActionName() {
    return NEXT_NAME;
  }

  public String getPreviousOccurenceActionName() {
    return PREVIOUS_NAME;
  }

  public void setModel(final JUnitRunningModel model) {
    myModel = model;
    myModel.addListener(new JUnitAdapter() {
      public void doDispose() {
        myModel = null;
      }
    });
  }

  private FailedTestInfo getNextOccurenceInfo() {
    return new NextFailedTestInfo().execute();
  }

  private FailedTestInfo getPreviousOccurenceInfo() {
    return new PreviousFailedTestInfo().execute();
  }

  abstract class FailedTestInfo {
    private TestProxy myDefect = null;
    private List<TestProxy> myAllTests;
    private List<TestProxy> myDefects;

    private TestProxy getDefect() {
      return myDefect;
    }

    private OccurenceNavigator.OccurenceInfo getOccurenceInfo() {
      return new OccurenceNavigator.OccurenceInfo(getSourceLocation(), getDefectNumber(), getDefectsCount());
    }

    private int getDefectNumber() {
      return myDefect == null ? getDefectsCount() : myDefects.indexOf(myDefect);
    }

    private Navigatable getSourceLocation() {
      return TestsUIUtil.getOpenFileDescriptor(myDefect, myModel);
    }

    public FailedTestInfo execute() {
      final int selectionIndex = calculateSelectionIndex();
      if (selectionIndex == -1)
        return this;
      final TestProxy defect = findNextDefect(selectionIndex);
      if (defect == null)
        return this;
      if (defect != myModel.getSelectedTest()) {
        myDefect = defect;
        return this;
      }
      final int defectIndex = myDefects.indexOf(defect);
      if (defectIndex == -1 || defectIndex == getBoundIndex())
        return this;
      myDefect = myDefects.get(nextIndex(defectIndex));
      return this;
    }

    private TestProxy findNextDefect(final int startIndex) {
      for (int i = nextIndex(startIndex); 0 <= i && i < myAllTests.size(); i = nextIndex(i)) {
        final TestProxy nextDefect = myAllTests.get(i);
        if (Filter.DEFECTIVE_LEAF.shouldAccept(nextDefect))
          return nextDefect;
      }
      return null;
    }

    protected abstract int nextIndex(int defectIndex);

    protected abstract int getBoundIndex();

    private int calculateSelectionIndex() {
      myAllTests = myModel.getRoot().getAllTests();
      myDefects = Filter.DEFECTIVE_LEAF.select(myAllTests);
      return myAllTests.indexOf(myModel.getSelectedTest());
    }

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
