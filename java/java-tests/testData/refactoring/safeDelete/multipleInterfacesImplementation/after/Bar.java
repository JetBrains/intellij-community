public class Bar implements IFoo, IBar {
    public String getText() {
        return "hello";
    }

    public IBar getIBar() {
        return this;
    }
}
