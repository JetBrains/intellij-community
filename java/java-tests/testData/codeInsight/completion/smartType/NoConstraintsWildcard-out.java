
class FooBar {

  {
    addSourceRoot(JavaResourceRootType.RESOURCE);
  }

  public static <P extends JpsElement> void addSourceRoot(JpsModuleSourceRootType<P> rootType) {

  }

  interface JpsElement {}

  interface JpsModuleSourceRootType<P extends JpsElement>  {}

  static class JavaResourceRootProperties extends JpsElementBase<JavaResourceRootProperties>{}
  static class JpsElementBase<Self extends JpsElementBase<Self>>  implements JpsElement {}

  static class JavaResourceRootType implements JpsModuleSourceRootType<JavaResourceRootProperties> {
    public static final JavaResourceRootType RESOURCE = new JavaResourceRootType();
    public static final JavaResourceRootType TEST_RESOURCE = new JavaResourceRootType();
  }

}