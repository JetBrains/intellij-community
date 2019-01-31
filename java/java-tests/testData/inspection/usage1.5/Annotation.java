public class Annotation {

  @<error descr="Usage of API documented as @since 1.7+">SafeVarargs</error>
  public final void a(java.util.List<String>... ls) {}
}