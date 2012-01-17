// "Add constructor parameter" "true"
class A {
  @javax.annotation.Nonnull private final Object field;

  A(@javax.annotation.Nonnull Object field, String... strs) {
      this.field = field;<caret>
  }

}