class CssPropertyValueImpl extends CssTableValueBase<CssPropertyValue, Object> implements CssPropertyValue {
  public CssPropertyValueImpl(final Type type) {
    super(type);
  }
}

abstract class CssTableValueBase<V extends CssTableValue, T> implements CssTableValue<V, T> {

  protected CssTableValueBase(final Type type) {
  }

  protected CssTableValueBase(final T value) {
  }
}

enum Type {}

interface CssTableValue<A, B> {
}

interface CssPropertyValue extends CssTableValue<CssPropertyValue, Object> {
}
