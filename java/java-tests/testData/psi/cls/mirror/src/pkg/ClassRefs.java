package pkg;

class ClassRefs {
  @DefaultArgAnno(String.class)
  @NamedArgAnno(type = String.class)
  public static final Class<?> cls = String.class;  // class refs are set from class initaializer
}

@interface DefaultArgAnno {
  Class<?> value();
}

@interface NamedArgAnno {
  Class<?> type();
}
