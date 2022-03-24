import org.jetbrains.annotations.NotNull;

// IDEA-280534
class TagImpl {
  boolean test(TagImpl o) {
    if (!getKey().equals(o.getKey())) return false;
    return getComment().equals(o.getComment());
  }

  private final TagKey tagKey;
  private final String comment;

  public TagImpl(Number owner, String comment) {
    this.tagKey = new TagKey(owner);
    this.comment = comment;
  }

  @NotNull
  public TagKey getKey() {
    return tagKey;
  }

  public String getComment() {
    return comment;
  }
}

class TagKey {
  private final Number owner;

  TagKey(Number owner) {
    this.owner = owner;
  }
}