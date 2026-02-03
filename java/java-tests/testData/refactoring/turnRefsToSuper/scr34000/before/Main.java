public class Main {
    private static void install() {
        SimpleModel simpleModel = new SimpleModel();
        View view = new View(simpleModel);
    }
    public void foo() {
        SimpleModel simpleModel = new SimpleModel();
        simpleModel.doIt();
    }
}