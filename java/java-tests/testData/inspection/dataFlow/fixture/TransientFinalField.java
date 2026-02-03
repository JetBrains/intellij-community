final class Alive implements java.io.Serializable {
  private transient final Object elvis = new Object();
  @Override
  public String toString() {
    if (elvis != null) {
      return "uh-huh-huh";
    } else {
      return "the king is dead";
    }
  }
}