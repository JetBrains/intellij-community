public class BarImpl implements Bar {
    public String getText() {
        return "hello";
    }

    public IBar getIBar() {
        return this;
    }
}
