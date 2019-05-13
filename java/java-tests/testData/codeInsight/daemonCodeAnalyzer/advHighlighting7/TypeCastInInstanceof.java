@SuppressWarnings({"UnusedDeclaration"})
class C {
  boolean foo(final ConfigurableField<String> nameField) {
    return (Formatter<?>)nameField.getFormatter() instanceof DefaultFormatter;
  }
}

@SuppressWarnings({"UnusedDeclaration"})
interface Formatter<V>{}
class DefaultFormatter implements Formatter<Object>{}
interface ConfigurableField<V> {
  Formatter<V> getFormatter();
}
