class Foo {
   public Object createBean(Object parent) {
    try {
      <selection>if (parent != null) {
        try {
        }
        catch (Exception e) {
          return null;
        }
      }


      Object tag = null;</selection>

      tag = foo(tag);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    return null;
  }

  private Object foo(final Object tag) {
    return null;
  }
}