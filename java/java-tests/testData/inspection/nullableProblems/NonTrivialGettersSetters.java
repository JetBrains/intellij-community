import org.jetbrains.annotations.NotNull;

class Foo {
  private static final Long DEFAULT_ID = -1L;

  @NotNull
  private Long id = DEFAULT_ID;

  public Long getId() {
    return id > 0 ? id : 42;
  }

  public void setId(Long id) {
    this.id = id != null ? id : DEFAULT_ID;
  }
}