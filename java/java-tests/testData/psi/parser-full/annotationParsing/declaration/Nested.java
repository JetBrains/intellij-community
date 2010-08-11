@interface Annotation {
  Class foo () default String.class;

  @interface Inner {
    String bar () default "<unspecified>";
  }
}