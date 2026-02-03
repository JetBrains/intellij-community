import java.util.function.*;

class ConstructorReferences {
  public ConstructorReferences() {}
  public ConstructorReferences(String s) {}
  public ConstructorReferences(String s1, String s2) {}
  public ConstructorReferences(ConstructorReferences other) {}
  public ConstructorReferences(ConstructorReferences other1, ConstructorReferences other2) {}

  public static void main(String[] args) {
    Supplier<ConstructorReferences> supplier = ConstructorReferences::new;
    Function<String,ConstructorReferences> function = ConstructorReferences::new;
    BiFunction<String,String,ConstructorReferences> bifunction = ConstructorReferences::new;
    UnaryOperator<ConstructorReferences> unaryOperator = ConstructorReferences:: new;
    Function<ConstructorReferences,ConstructorReferences> unaryOperatorBaseClass = ConstructorReferences::new;
    BinaryOperator<ConstructorReferences> binaryOperator = ConstructorReferences::new;
    BiFunction<ConstructorReferences, ConstructorReferences, ConstructorReferences> binaryOperatorBaseClass = ConstructorReferences::new;
    Consumer<ConstructorReferences> consumer = ConstructorReferences::new;
    BiConsumer<ConstructorReferences,ConstructorReferences> biconsumer = ConstructorReferences::new;
  }
}
