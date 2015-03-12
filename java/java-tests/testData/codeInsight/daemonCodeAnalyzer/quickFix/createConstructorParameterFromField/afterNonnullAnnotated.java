// "Add constructor parameter" "true"
package javax.annotation;
class A {
  @Nonnull private final Object field;

  A(@javax.annotation.Nonnull Object field, String... strs) {
      this.field = field;<caret>
  }

}

@interface Nonnull {}