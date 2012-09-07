import org.jetbrains.annotations.Nullable;
class TestIDEAWarn {
  void test(java.util.Map<Object, Object> values) {
    boolean condition = false;
    Object target = getTarget();
    for (Object o : values.values()) {
      if (o != null && o != target) {
        condition = true;
      }
    }
    if (!condition || target == null) {
      System.out.println(target);
    }
  }

  @Nullable
  public Object getTarget() {
    return null;
  }

}
