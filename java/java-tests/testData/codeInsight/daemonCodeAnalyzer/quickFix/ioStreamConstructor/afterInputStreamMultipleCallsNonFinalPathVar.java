// "Fix all ''InputStream' and 'OutputStream' can be constructed using 'Files' methods' problems in file" "true"
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Paths;

class Foo {
  void test(File file, String str) {
    Path filePath = file.toPath();
    try (InputStream is = Files.newInputStream(filePath)) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    
    filePath = Paths.get("other");

    try (InputStream is = Files.newInputStream(file.toPath())) {
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Path strPath = Paths.get(str);
    for (int i = 0; i < 10; i++) {
      try (InputStream is = Files.newInputStream(Paths.get(str))){
      } catch(IOException e){
        throw new RuntimeException(e);
      }

      strPath = Paths.get("foo");
      try (InputStream is = Files.newInputStream(strPath)) {
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}