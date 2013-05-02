package pkg;

class ClassRefs {
  @AnnWithTypeLocal(type = String.class)
  public static final Class<?> cls = String.class;  // class refs are set from class initaializer
}

@interface AnnWithTypeLocal {
  Class type();
}
