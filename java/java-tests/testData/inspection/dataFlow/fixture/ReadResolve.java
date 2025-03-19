import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Base64;

class Test implements Serializable {
  @NotNull String string;

  Test(@NotNull String string) {
    this.string = string;
  }

  @Serial
  protected Object readResolve() {
    if (string == null) {
      throw new IllegalStateException("Wrong object!");
    }
    return this;
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException {
    byte[] data = Base64.getDecoder().decode("rO0ABXNyAARUZXN0ST9d8XKvH/0CAAFMAAZzdHJpbmd0ABJMamF2YS9sYW5nL1N0cmluZzt4cHA=");
    Test object = (Test) new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
    System.out.println(object);
  }
}