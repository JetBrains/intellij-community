import java.util.Collection;

class Test {
  Expression expression;

  void foo() {
    expression.in(parameter(Collection.class));
  }

  <T> Expression<T> parameter(Class<T> paramClass) {
    return null;
  }

  interface Expression<T> {
    Expression in(Expression<Collection<?>> values);
  }
}