// "Add constructor parameter" "true"
package javax.annotation;
class A {
  @Nonnull private final Object field;

  A(@Nonnull Object field, String... strs) {
      this.field = field;
  }

}

@interface Nonnull {}