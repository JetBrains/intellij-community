import java.util.Set;

class Test {

  private static class Form<E, L extends Logic<E>> {
    public void process(L logic, Set<FieldModel<E, ?>> fieldModels) {
      fieldModels.forEach(logic::declareField);
    }
  }
  private static class FieldModel<E, T> {
  }

  private static class Logic<E> {
    public <T> void declareField(FieldModel<E, T> field) {
    }
  }
}