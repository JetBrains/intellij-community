import java.util.*;
abstract class GenericPanelControl {
    protected abstract void _performAction(List<?> rowVector);
}

class WorkflowPanelControl extends GenericPanelControl {
    protected void _performAction(List rowVector) {
    }
}

class WorkflowSubPanelControl extends WorkflowPanelControl {
    protected void _performAction(List rowVector) {
        super._performAction(rowVector);
    }
}