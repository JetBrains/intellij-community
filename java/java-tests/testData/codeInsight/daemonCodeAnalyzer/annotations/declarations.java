@interface Declarations <error descr="'extends' not allowed on @interface">extends</error> java.io.Serializable {

  int x() <error descr="'throws' not allowed on @interface method">throws</error> IllegalArgumentException;
}
@interface Seconds <error descr="'permits' not allowed on @interface">permits</error> Declarations {

  <error descr="@interface members may not have type parameters"><T></error> int y();
}
@interface Generic<error descr="@interface may not have type parameters"><T></error> {}