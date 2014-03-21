import java.util.function.Function;

class ChoiceBox<T> {
  static {
    ChoiceBox<Item> confParamField1 = new ChoiceBox<>("", p -> p.getName());
    ChoiceBox<Item> confParamField2 = new ChoiceBox<Item>("", p -> p.getName());
  }

  public ChoiceBox(T... options) {}

  public ChoiceBox(String caption, Function<T, String> itemCaption) {}

  public static class Item {
    public String getName() {
      return null;
    }
  }
}