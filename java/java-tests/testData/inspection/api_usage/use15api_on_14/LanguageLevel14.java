class Main {
  {
    g(<error descr="Usage of API documented as @since 15+">"%s".formatted</error>(1),
      <error descr="Usage of API documented as @since 15+">"".stripIndent</error>(),
      <error descr="Usage of API documented as @since 15+">"".translateEscapes</error>());
  }

  private void g(String formatted, String stripIndent, String translateEscapes) {}
}