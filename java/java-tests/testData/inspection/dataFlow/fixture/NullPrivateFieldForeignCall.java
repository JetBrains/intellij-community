import org.jetbrains.annotations.*;

public class NullPrivateFieldForeignCall {
  private static @Nullable Object value = null;
  private @Nullable Object instanceValue = null;
  @Nullable Object packageValue = null;

  private static void takesNonNull(@NotNull Object value) {
  }

  private static void assignedNull() {
    value = null;
    External.nonPureMethod();
    takesNonNull(<warning descr="Argument 'value' might be null">value</warning>);
  }

  private static void checkedNull() {
    if (value == null) {
      External.nonPureMethod();
      takesNonNull(<warning descr="Argument 'value' might be null">value</warning>);
    }
  }

  private static void checkedNotNull() {
    if (value != null) {
      External.nonPureMethod();
      takesNonNull(value);
    }
  }

  private void instanceField() {
    instanceValue = null;
    External.nonPureMethod();
    takesNonNull(<warning descr="Argument 'instanceValue' might be null">instanceValue</warning>);
  }

  private void packagePrivateField() {
    packageValue = null;
    External.nonPureMethod();
    takesNonNull(<warning descr="Argument 'packageValue' might be null">packageValue</warning>);
  }

  private static void sameClassCall() {
    value = null;
    init();
    takesNonNull(<warning descr="Argument 'value' might be null">value</warning>);
  }

  private static void nestedClassCall() {
    value = null;
    Nested.init();
    takesNonNull(<warning descr="Argument 'value' might be null">value</warning>);
  }

  private static void init() {
    value = External.create();
  }

  static class Nested {
    static void init() {
      value = External.create();
    }
  }
}

final class External {
  static native void nonPureMethod();

  static native Object create();
}
