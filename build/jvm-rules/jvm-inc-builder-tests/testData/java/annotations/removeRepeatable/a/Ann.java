import java.lang.annotation.Repeatable;

@Repeatable(Anns.class)
public @interface Ann {
  int v();
}
