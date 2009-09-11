public class Main {
    private static void install() {
        Model simpleModel = new SimpleModel();
        View view = new View(simpleModel);
    }
    public void foo() {
        Model simpleModel = new SimpleModel();
        simpleModel.doIt();
    }
}