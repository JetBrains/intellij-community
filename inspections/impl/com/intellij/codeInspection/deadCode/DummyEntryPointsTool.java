package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.util.RefFilter;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class DummyEntryPointsTool extends FilteringInspectionTool {
  private RefEntryPointFilter myFilter;
  private DeadCodeInspection myOwner;
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsTool(DeadCodeInspection owner) {
    myOwner = owner;
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new RefEntryPointFilter();
    }
    return myFilter;
  }

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {}

  public void exportResults(Element parentNode) {}

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[0];
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.dead.code.entry.points.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return "";
  }

  public HTMLComposerImpl getComposer() {
    return new DeadHTMLComposer(this);
  }

  public GlobalInspectionContextImpl getContext() {
    return myOwner.getContext();
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious()};
    }
    return myQuickFixActions;
  }

  private class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious() {
      super(InspectionsBundle.message("inspection.dead.code.remove.from.entry.point.quickfix"), null, null, DummyEntryPointsTool.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      final EntryPointsManager entryPointsManager =
        getContext().getExtension(GlobalJavaInspectionContextImpl.CONTEXT).getEntryPointsManager(getContext().getRefManager());
      for (RefElement refElement : refElements) {
        entryPointsManager.removeEntryPoint(refElement);
      }

      return true;
    }
  }
}
