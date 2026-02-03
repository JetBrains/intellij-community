// "Add constructor parameter" "true"
package javax.annotation;
class A {
  @Nonnull private final Object <caret>field;

  A(String... strs) {
  }

}

@interface Nonnull {}