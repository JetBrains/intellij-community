import java.io.File;

class Some {
  private File findRepository(File file) {
    while (file != null) {
      file = file.getParentFile();
    }

    return new File("foo");
  }

}


