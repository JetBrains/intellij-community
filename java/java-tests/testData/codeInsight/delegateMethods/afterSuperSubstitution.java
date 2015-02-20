public class K {
    void kkkk(){}
}

class KImpl extends K {}

abstract class InspectionToolWrapper<T extends K> {
    T myTool;

    protected InspectionToolWrapper(T tool) {
        myTool = tool;
    }

    public T getTool() {
        return myTool;
    }
}

class CommonInspectionToolWrapper extends InspectionToolWrapper<KImpl>{
    protected CommonInspectionToolWrapper(KImpl tool) {
        super(tool);
    }

    public void kkkk() {
        myTool.kkkk();
    }
}